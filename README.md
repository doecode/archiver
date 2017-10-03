# archiver
Support REST client for archiving and repository backup service associated with DOECode. Used to cache repositories or archived
source projects to local file storage.

## Purpose
The archiver project is intended to keep local cached / cloned copies of DOE source code projects for backup and storage purposes.
Should hosting services of such projects be lost, they might be recovered in some form from this archive service's local file storage.

This project is intended to be of use directly from DOECode's [server project](https://github.com/doecode/server) for API caching
support, and not exposed directly to other access.

## Setup and Configuration
The archiver needs merely a designated file storage area (a folder) in which
to keep its downloaded files.  If repository links are posted, attempts are
made to call git to locally fetch all current branches of the project to 
file storage areas.  Optionally, a periodic external process will keep these
external archives up to date via various "git fetch" commands.

In the case of file uploads, the file will be stored,
and its content extracted if possible (for archive files).  Such archive uploads will not be maintained past point-in-time of archiving.

| Property Name | Purpose |
| --- | --- |
| ${file.archive} | Filesystem location (on archiver server) to store cached files and temporary git repositories. |

