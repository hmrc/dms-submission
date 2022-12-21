## Design decisions

### `cats-effect` and `fs2` usage

As this service is intended to be used by multiple consumers to submit files to DMS. We wanted to make sure that we
reduce the necessary work by consuming parties as much as possible. The main way we have done this is to provide a
high level of resiliency to downstream failure.

This is done using scheduled jobs to move submissions through the various states they need to be in. Historically, we
would have used the akka scheduler for this, however, given the current state of akka and its uncertain place within
Play framework & HMRC we decided to avoid any explicit dependencies on it.

We tried a few options but settled on cats-effect and fs2 to enable us to build scheduled jobs and retry behaviour.
These are used elsewhere within HMRC but are considered quite complex libraries.

In order to further hedge our bets, we have restricted the usage of these libraries to service classes which implement
the desired behaviour [SchedulerService](/app/services/SchedulerService.scala) and [RetryService](/app/services/RetryService.scala).
This means that if another platform-endorsed solution to this is found, they can be easily refactored.

### Temporary file usage

Further to our efforts to avoid explicit akka usage, when integrating with object-store and creating payloads locally,
we decided to use temporary files instead of akka streams. This isn't something that is usually done on services and
we wanted to make sure that it was robust, so there were a few main considerations:

#### Cleaning up temporary files

There are two mechanisms that create temporary files in the service:
- Play itself when it receives a payload with files in it (like the PDF submissions we receive). 
- When building the zip file to submit to DMS we create a temporary local directory and zip it

There is a mechanism within Play to automatically remove the files _it_ creates when they are no longer in use
([see documentation](https://www.playframework.com/documentation/2.8.x/ScalaFileUpload#Cleaning-up-temporary-files)).
However, this does not apply to the temporary files we create. 

For the files we create, we always do so using the [FileService](/app/services/FileService.scala) within its
`withWorkingDirectory` method, which guarantees removal of the file once the provided function completes. (Including if
it fails). This is thoroughly tested.

We have also included a custom dashboard in grafana which shows the temporary file usage of each container. Even during
performance tests this never reaches a particularly large number, and always returns to 0.

#### Making sure we don't block the main thread

As most of the file system operations we perform are synchronous, we wanted to avoid performing any of them on Play's
main thread. There is a [FileSystemExecutionContext](/app/config/FileSystemExecutionContext.scala) that we use for any
file system operations to prevent this.
