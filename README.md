# Docker Swarm orchestration and service discovery with Spring
This project has two API's that communicate with each other using Consul for service discovery. The README explains how to create a Docker Swarm cluster and deploy these two API's using Swarm on multiple hosts.

## Creating the Swarm Cluster
### Consul
We will use overlay multi-host network, so we need Consul. Let's create a node on AWS that will run Consul

```bash
$ docker-machine create -d amazonec2 \
    --amazonec2-access-key *** --amazonec2-secret-key *** --amazonec2-region eu-west-1 --amazonec2-vpc-id vpc-*** \
    consul01
```

Consul will run inside a Docker container. Let's point or Docker client to the node that we just created and run the Consul container

```bash
$ eval "$(docker-machine env consul01)"
$ docker run -d -p 8400:8400 -p 8500:8500 -p 8600:8600 progrium/consul -server -bootstrap -data-dir /data
```

### Swarm Managers
Now we can create our Swarm cluster using the Consul that we just started. First, our Swarm Manager

```bash
$ docker-machine create -d amazonec2 \
    --amazonec2-access-key *** --amazonec2-secret-key *** --amazonec2-region eu-west-1 --amazonec2-vpc-id vpc-*** \
    --swarm-master \
    --swarm-discovery="consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-advertise=eth0:0" \
    --swarm-opt="advertise=eth0:3376" \
    swarm-master-01
```

### Swarm Agents
Then our Swarm agents

```bash
$ docker-machine create -d amazonec2 \
    --amazonec2-access-key *** --amazonec2-secret-key *** --amazonec2-region eu-west-1 --amazonec2-vpc-id vpc-*** --amazonec2-instance-type=t2.small \
    --swarm \
    --swarm-discovery="consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-advertise=eth0:0" \
    swarm-agent-01
```

```bash
$ docker-machine create -d amazonec2 \
    --amazonec2-access-key *** --amazonec2-secret-key *** --amazonec2-region eu-west-1 --amazonec2-vpc-id vpc-*** --amazonec2-instance-type=t2.small \
    --swarm \
    --swarm-discovery="consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-store=consul://$(docker-machine ip consul01):8500" \
    --engine-opt="cluster-advertise=eth0:0" \
    swarm-agent-02
```

The cluster is done! All these machines shared a new security group called `docker-machine` with inbound ports opened for the Swarm cluster to work. Since we need extra ports for our applications (8000, 80, etc), we can open these ports now on the AWS console.

## Creating an overlay network
If we want to start using our Swarm cluster, we need to tell our Docker client to point to the Swarm Manager. All following commands will point to it.

```bash
$ eval "$(docker-machine env --swarm swarm-master-01)"
```

Let's create the overlay multi-host network

```bash
$ docker network create --driver overlay consul-net
```

## Running our applications
The applications are two API's `service1` and `service2` that expose the `/users` endpoint. When you call this endpoint on `service1`, this will call the same endpoint on `service2` and show its response. The `service1` knows which `ip` and `port` use to communicate with `service2` because it uses Consul to discover it. It uses client side load balancing, so every request from `service1` to `service2` may end up on different `service2` instances.

Every instance for each API has its own unique `instance_id`, which is a random string generated starting with "instance_" on the application startup. An example is `instance_gfas42asddsfsd83asd`.

### Service discovery for our applications
We can't use the Consul that we are already using for Swarm, since is not in the same network as our containers, so we need to run a Consul container within the same network. All containers that need access to Consull will be part of this network, and they can use the name `consul` to access Consul.

```bash
$ docker run --name=consul -d --net=consul-net -p 8400:8400 -p 8500:8500 -p 8600:8600 progrium/consul -server -bootstrap -data-dir /data
```

Please, notice that the container must run in the overlay network that we just created a moment ago.

### Publish docker images for our applications
The containers have everything they need to run, without volumes, so we need to build and publish images for the current version of the applications into a private Docker registry. Notice that the `DOCKER_REGISTRY` variable should be the same you used in the docker-compose file. The `VERSION` variable will contain the current timestamp, but you can use whatever you want to tag your images.

```bash
$ ./gradlew clean build
$ docker-compose build
$ DOCKER_REGISTRY="your-registry.com"
$ VERSION=`date +%s`
$ docker tag $DOCKER_REGISTRY/service1 localhost:5000/service1:$VERSION
$ docker tag $DOCKER_REGISTRY/service2 localhost:5000/service2:$VERSION
$ docker push $DOCKER_REGISTRY/service1:$VERSION
$ docker push $DOCKER_REGISTRY/service2:$VERSION
```

Since the number of services in our docker-compose file may vary, we could use [this script to do this dynamically](https://github.com/jpetazzo/orchestration-workshop/blob/master/bin/build-tag-push.py).

### Starting and scaling the applications
Now we are ready to start our application

```bash
$ docker-compose up -d
$ docker-compose ps
```

Let's scale it and see how the load balancer works

```bash
$ docker-compose scale service1=3 service2=3
$ docker-compose ps
```

### Testing the load balancing and discovery
Open a terminal with the logs for the applications

```bash
$ docker-compose logs
```

See in which IP is the `service1` running and hit it with curl (any `service1` instance is fine). Then see the logs to read the instance id from `service1` and `service2` that were hit by the request

```bash
$ docker ps --filter name=service1
$ docker ps --filter name=service2
$ curl -i IP:PORT/users
```

In the first terminal you'll see two strings being logged. The first one is the `service1` instance id that will always be the same since you are directly hitting an specific ip and port. But the second line is the `service2` instance id, that will vary depending on the load balancing.

## Monitoring containers
Now let's test what happens if a container randomly crashes. Check how many containers are running, and leave the console listening for Docker events

```bash
$ eval "$(docker-machine env --swarm swarm-master-01)"
$ docker-compose events
```

In our `docker-compose` file we've chosen a policy that will automatically restart our containers when they fail. So let's kill one of them to see how Docker automatically starts it again. For that, open another terminal and ssh into one of the swarm nodes, find the process running inside our docker containers (in our case is a java process), and kill one of them. That will stop the container as if it would've failed.

```bash
$ docker-machine ssh swarm-agent-01
$ ps aux | pgrep -a java
$ kill -9 PID_THAT_YOU_CHOOSE
```

At this point, you should see in the first terminal the events related with the failure and startup of the killed container. Something like

```bash
2016-03-16 11:47:02.359442 container die 1d782035fd0c604f83262cf4bcf9b85c3e7d2b77fc05995f90af1646d7adc849 (image=registry.io/service1 node:swarm-agent-01, name=springconsulswarm_service1_3)
2016-03-16 11:47:02.459683 container start 1d782035fd0c604f83262cf4bcf9b85c3e7d2b77fc05995f90af1646d7adc849 (image=registry.io/service1 node:swarm-agent-01, name=springconsulswarm_service1_3)
```

So even though our container _failed_, Docker automatically started it again.

### Limitations
Currently, if one of the Docker Swarm nodes crashes, Docker will not restart the containers that were running in that node, and those running containers will disappear.

## Resources
- [Docker Swarm and overlay network](https://docs.docker.com/engine/userguide/networking/get-started-overlay/)
- [Consul HTTP](https://www.consul.io/docs/agent/http.html)
- [Spring Cloud Consul](http://cloud.spring.io/spring-cloud-consul/spring-cloud-consul.html)