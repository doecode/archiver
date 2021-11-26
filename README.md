# archiver
Support REST client for archiving and repository backup service associated with DOECode. Used to cache repositories or archived
source projects to local file storage.

## Purpose
The archiver project is intended to keep local cached / cloned copies of DOE source code projects for backup and storage purposes.
Should hosting services of such projects be lost, they might be recovered in some form from this archive service's local file storage.

This project is intended to be of use directly from DOECode's [server project](https://github.com/doecode/server) for API caching
support, and not exposed directly to other access.

As of version 1.3, subversion support has been added alongside git for remote
repository mirroring/caching purposes.

## Setup and Configuration
The archiver needs merely a designated file storage area (a folder) in which
to keep its downloaded files.  If repository links are posted, attempts are
made to call git to locally fetch all current branches of the project to 
file storage areas.  Optionally, a periodic external process will keep these
external archives up to date via various "git fetch" commands.

In the case of file uploads, the file will be stored,
and its content extracted if possible (for archive files).  Such archive uploads will not be maintained past point-in-time of archiving.

The maven build environment (as of version 1.3) supports the shared-resources
properties sharing introduced on the DOE CODE API "server" project.  This
means the configuration property the application uses is taken from an 
environment-specific (default name "development.properties") standard Java
properties file, located in ${user.home}/shared-resources/doecode/ folder in
each developer's build environment.

Each properties file may be configured according to your individual environment
needs (testing, acceptance, production, etc.) by simplying creating new
properties files in the aforementioned folder and referring to them via the
maven variable "environment" either through a -D mvn build switch, or a user-
specific profile in your own build environment.

| Property Name | Purpose |
| --- | --- |
| ${file.archive} | Filesystem location (on archiver server) to store cached files and temporary repositories. |
| ${file.limited.archive} | Filesystem location (on archiver server) to store cached files for limited software. |
| ${site.url} | (optional) Base URL of the client front-end services. |
| ${email.host} | (optional) SMTP host name for sending notification emails. |
| ${email.from} | (optional) The address to use for sending above emails. |
| ${file.approval.email} | (optional) Email address for sending File Approval emails.  Requires email.host and email.from properties. |
| ${laborhours.cloc} | (optional) Full path location to the "cloc" program that calculates SLOC for labor hours. |
| ${laborhours.cocomoii} | (optional) URL used to calculate effort based on SLOC via COCOMO II methodology. |
