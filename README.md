
# dms-submission

This service provides a mechanism to send pdfs to DMS.

This mechanism is asynchronous so requires that consuming services implement a callback endpoint to receive updates 
on the status of their submission.

## How to use

Bear in mind when integrating that dms-submission will provide _some_ resilience against transitive failures. 
If your submission request is accepted, we will retry calls against SDES until they accept. And when we receive 
callbacks from SDES, we will repeatedly call your service until we receive a response.

### Prerequisites

#### Internal Auth

In order to make submission calls there are a number of prerequisites steps that you'll need to follow.

First, you'll need to make sure that your service has an `internal-auth` grant configured to access the service. 
These are configured in the [internal-auth-config](https://github.com/hmrc/internal-auth-config) repository. 
The grant should be the following:

```json
{
  "grantees": [
    {
      "granteeType": "service",
      "identifiers": ["<YOUR-SERVICE-NAME>"]
    }
  ],
  "permissions": [
    {
      "resourceType": "dms-submission",
      "resourceLocation": "submit",
      "actions": ["WRITE"]
    }
  ]
}
```

You'll also need to make sure that requests are made using an internal-auth token configured for your service. 
For deployed environments, contact Team Build and Deploy to get them to create and encrypt a token for you.

We recommend that you use the configuration key `internal-auth.token` for this, as it is used by other platform 
libraries.

#### Callback endpoint

In order to notify you of changes in the status of your submission, `dms-submission` will call your service on an 
endpoint you specify in the submission request. The request that will be sent will be a POST to your provided 
callback url with the following body:

When accepted by SDES:
```json
{
  "correlationId": "71378476-272e-48c4-ac8f-b18af8dbc8f4",
  "status": "Processed",
  "objectSummary": {
    "location": "dms-submission/your-service/71378476-272e-48c4-ac8f-b18af8dbc8f4",
    "contentLength": 1337,
    "contentMd5": "<SOME_HASH>",
    "lastModified": "2022-02-01T12:00:00"
  }
}
```

When there has been a downstream failure:
```json
{
  "correlationId": "71378476-272e-48c4-ac8f-b18af8dbc8f4",
  "status": "Failed",
  "objectSummary": {
    "location": "dms-submission/your-service/71378476-272e-48c4-ac8f-b18af8dbc8f4",
    "contentLength": 1337,
    "contentMd5": "<SOME_HASH>",
    "lastModified": "2022-02-01T12:00:00"
  },
  "failureReason": "Some failure"
}
```
> NOTE: The failure reason is whatever the failure reason SDES returns

### Making submission calls

The `dms-submission/submit` endpoint accepts a request with a `Multipart/Form-Data` body with the following fields:

> TODO - Provide better documentation for metadata fields when we know more about them

| Field name                  | Description                                                                                                                                                                   | Type      |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| id                          | This field is optional, if provided callbacks will use this instead of a randomly generated ID to inform you of the status of your submission                                 | String    |
| callbackUrl                 | This is the url which should be used to notify you of the outcome of your submission. This should be a fully-qualified URL and can be `localhost` based in local environments | String    |
| form                        | This field is the actual pdf which should be sent to DMS                                                                                                                      | File      |
| metadata.store              | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | Boolean   |
| metadata.source             | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.timeOfReceipt      | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | Timestamp |
| metadata.formId             | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.numberOfPages      | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | Int       |
| metadata.customerId         | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.submissionMark     | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.casKey             | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.classificationType | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |
| metadata.businessArea       | This will be used in the `metadata.xml` for GIS/DMS                                                                                                                           | String    |

Here is an example of creating this request using the `HttpClientV2` from `http-verbs`

```scala
val clientAuthToken = configuration.get[String]("internal-auth.token")

httpClient.url("http://localhost:8222/dms-submission/submit")
  .withHttpHeaders(AUTHORIZATION -> clientAuthToken)
  .post(
    Source(Seq(
      DataPart("callbackUrl", s"http://localhost:<MY_SERVICE_PORT>/callback"),
      DataPart("metadata.store", "true"),
      DataPart("metadata.source", "<MY_SERVICE>"),
      DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(timeOfReceipt)),
      DataPart("metadata.formId", "formId"),
      DataPart("metadata.numberOfPages", "1"),
      DataPart("metadata.customerId", "customerId"),
      DataPart("metadata.submissionMark", "submissionMark"),
      DataPart("metadata.casKey", "casKey"),
      DataPart("metadata.classificationType", "classificationType"),
      DataPart("metadata.businessArea", "businessArea"),
      FilePart(
        key = "form",
        filename = "form.pdf",
        contentType = Some("application/octet-stream"),
        ref = Source.single(ByteString("Hello, World!")),
        fileSize = 1337
      )
    ))
  )
```

> NOTE: The `FilePart` above takes any `Source[ByteString, _]`

The possible responses for this are:

| Response status    | Example body                                    | Description                                                                                                                                       |
|--------------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| ACCEPTED (202)     | `{"id":"71378476-272e-48c4-ac8f-b18af8dbc8f4"}` | This status means that your request has been accepted and the returned `id` is what will be used in subsequent calls in regard to this submission |
| BAD_REQUEST (400)  | `{"errors": ["some error"]}`                    | This status means that there is an issue with the request you've submitted, the `errors` array should contain all known errors for the request    |
| UNAUTHORIZED (401) | `{}`                                            | This status means that you have incorrectly configured the auth token for your request                                                            |
| FORBIDDEN (403)    | `{}`                                            | This status means that you have incorrectly configured the internal-auth configuration for your service                                           |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").