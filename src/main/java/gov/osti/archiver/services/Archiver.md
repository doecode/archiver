DOECode Archiver Service
==================

Introduction
------------

Caching and preservation of external DOECode-based software packages and repositories.  The HTTP `GET` verb is used to retrieve information in JSON, while `POST` is used to send or update information on archive projects.

> The API is available via `/archiver/project` on the server.

[Archiver on GitHub >](https://github.com/doecode/archiver)
[DOECode main project on GitHub >] (https://github.com/doecode/doecode)

HTTP Request Methods
--------------------

| Method | Description |
| --- | --- |
| `GET` | Used to retrieve resources |
| `POST` | Create or update resources |
| `PUT` | *Not currently used* |
| `DELETE` | *Not currently used* |

HTTP Response Codes
-------------------

Most relevant service endpoints share common HTTP response codes, with the most
common ones with typical reasons included below.

| Response Code | Description |
| --- | --- |
| 200 | OK, request was processed successfully |
| 400 | Bad Request, such as validation error or bad JSON |
| 404 | Requested resource not found |
| 500 | Internal error or database issue |

Service Endpoints
-----------------

## Information Retrieval

Information retrieval API for obtaining general information about the projects and states of the preservation records.

### retrieve single record

`GET /archiver/project/{projectId}`

Retrieve information about a given project by its unique *projectId* value as a JSON single Object.  One project may be associated with multiple
DOECode metadata records.  The status attribute of a project is listed below:

| Status Value | Description |
| --- | --- |
| Pending | Currently processing or awaiting checkout thread to be cached. |
| Complete | Processing is complete and the project is successfully cached. |
| Error | An error occurred caching the project, with details available in the "status_message" element. |

> Request:
> ```html
> GET /archiver/project/234
> Content-Type: application/json
> ```
> Response:
> ```html
> HTTP/1.1 200 OK
> Content-Type: application/json
> ```
> ```json 
> {"project_id":7492,"repository_link":"https://github.com/doecode/server","status":"Complete","status_message":"CREATED","repository_type":"Git","date_record_added":"2017-10-06","date_record_updated":"2017-10-06","code_ids":[6443]}
>```

### retrieve via CODE ID value

`GET /archiver/project/codeid/{id}`

Attempt to look up an archived project by its associated DOECode CODE ID value.  Returns a JSON array of matching records if any found.

> Request:
> ```html
> GET /archiver/project/codeid/6443
> Content-Type: application/json
> ```
> Response:
> ```html
> HTTP/1.1 200 OK
> Content-Type: application/json
> ```
> ```json
> [{"project_id":7492,"repository_link":"https://github.com/doecode/server","status":"Complete","status_message":"CREATED","repository_type":"Git","date_record_added":"2017-10-06","date_record_updated":"2017-10-06","code_ids":[6443]}]
> ```

### projects by status

`GET /archiver/project/status/{value}`

Obtains a JSON array of all project records in a given status value.  Case is relevant, and the valid values are: Pending, Complete, or Error.  Unacceptable status values will
return a 400 error response.  Note that presently ALL records of that state will be returned in this array.

> Request:
> ```html
> GET /archiver/project/status/Complete
> Content-Type: application/json
> ```
> Response:
> ```html
> HTTP/1.1 200 OK
> Content-Type: application/json
> ```
> ```json
> [{"project_id":7492,"repository_link":"https://github.com/doecode/server","status":"Complete","status_message":"CREATED","repository_type":"Git","date_record_added":"2017-10-06","date_record_updated":"2017-10-06","code_ids":[6443]}]
> ```


## Project Archive Submission

### cache a project

`POST /archiver/project`

Send JSON of a DOECode project to cache, containing the CODE ID value, and either a REPOSITORY LINK value of an external git repository, OR a posted FILE, as a
multipart form-data upload.  In the case of the latter, the file will be unpacked (if it is an recognizable archive format) and that content used as the cache.

The initial returned JSON will usually contain preliminary Pending information, as the caching process is asynchronous.  Retrieve more current information via
the GET endpoint for the indicated PROJECT ID to see its progress.

> Request:
> ```html
> POST /archiver/project
> Content-Type: application/json
> ```
> ```json
> { "code_id":9991, "repository_link":"http://github.com/username/myproject" }
> ```
> Response:
> ```html
> HTTP/1.1 200 OK
> Content-Type: application/json
> ```
> ```json
> { "project_id":2134,"repository_link":"http://github.com/username/myproject","status":"Pending","repository_type":"Git","date_record_added":"2017-10-09","date_record_updated":"2017-10-09","code_ids":[9991] }
> ```
