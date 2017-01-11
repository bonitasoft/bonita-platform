# bonita-platform

## What is bonita-platform?
Artifact to manage Bonita BPM platform:
* Engine database initial creation
* Platform configuration files (inside the database)
* Tomcat / WildFly bundle configuration.

## How to build it?
Simply run `mvn clean install` from the root folder (requires Java 7+ and Maven to run)

## How to use it?
See [Bonitasoft Documentation](http://documentation.bonitasoft.com/?page=BonitaBPM_platform_setup) on how to use this tool.


## Deprecation
**Deprecated**: From version **7.4.0** on, bonita-platform has been moved inside [Bonita BPM Engine](https://github.com/bonitasoft/bonita-engine/tree/master/platform).
It becomes a standard Maven module inside Bonita BPM Engine. Thus, there is nothing special to do to build this artifact.  
This Github repository becomes obsolete then.
