#Akka Cluster Example With Spray

This is a combination of the Akka cluster example with Spray.io. It uses very niave routing, which is only for demonstration purposes. 

Run with:

* ```sbt 'run-main main.TransformationFrontend 2551'```
* ```sbt 'run-main main.TransformationBackend 2552'```
* add new backend with ```sbt 'run-main main.TransformationBackend'```