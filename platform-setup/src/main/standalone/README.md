# Bonita BMP Platform Setup

## What it does?

Project **Bonita BPM Platform Setup** sets up Bonita BPM Platform before **Bonita BPM** can be run:

* creates the structure of the database (tables, primary / foreign keys, indexes, ...)
* inserts the default minimum data 


## Requirements
>     Java JDK 1.7 or higher

## Running bonita-platform-setup

### Configuration 

* configure access to the database in /conf/<db.vendor>.properties: set up database credentials
* logging can be configured in logback.xml      
* add JDBC drivers (specific to your database) in /lib folder (for Oracle and SqlServer only, as postgres and mySql drivers are already included) 

### Run

run shell using appropriate <db.vendor>. Possible value are: postgres, mysql, sqlserver, oracle

available action action :


```shell
setup.sh <db.vendor> init
```

 * All database structure (tables) will be created on target database (done only once).
 * All configuration files, licenses files under `platform_conf/initial` folder will be written in database.
 * All previous configuration will be overwritten


```shell
setup.sh <db.vendor> pull
```
  All configuration and licenses files currently in database will be written in the folder `platform_conf/current`

 

```shell
setup.sh <db.vendor> push
```
  All configuration files, licenses files under `platform_conf/current` will be written in database.
      All previous configuration will be overwritten
 

