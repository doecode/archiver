# archiver
Support REST client for archiving and repository backup service associated with DOECode. Used to cache repositories or archived
source projects using a GitLab installation service.

## Purpose
The archiver project is intended to keep local cached / cloned copies of DOE source code projects for backup and storage purposes.
Should hosting services of such projects be lost, they might be recovered in some form from this archive service's GitLab installation.

## Setup and Configuration
The archiver depends on an already-installed and configured [GitLab](https://about.gitlab.com/) installation (Community Edition or 
Enterprise).  The instance should be installed on a server accessible by the archiver deployment (need not be the same server). 

The archiver uses an API key generated within GitLab (any user will do) in order to maintain its cache/clones of projects.  Simply
obtain an API key from any configured user within your GitLab install, and supply that information in the archiver.properties 
configuration file elements (see below).

The configuration elements are read from the classpath in the archiver.properties file, as defined here:

| Property Name | Purpose |
| --- | --- |
| ${gitlab.url} | Base URL of local GitLab installation for API access. |
| ${gitlab.apikey} | API key of GitLab user to use for import/cache purposes. |
| ${file.archive} | Filesystem location (on archiver server) to store cached files and temporary git repositories. |


