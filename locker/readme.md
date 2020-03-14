Setting Tests for SQL Server locking module
================

This test uses a standalone database instance and therefore requires that you be running a local version of SQL Server.
The following are the pre-requisites
* **SQL Server Version**: SQL Server 2019 . Follow the steps [HERE](https://docs.microsoft.com/en-us/sql/linux/quickstart-install-connect-ubuntu?view=sql-server-ver15) to get it setup
* **Operating System**: Ubuntu 18.04
* **Killbill build version**: 0.22.z
* **Killbill build commons version**: 0.23.y

Steps
-----------------------
* Build the killbill commons and all modules successfully with maven
* Start your SQL server instance (it often starts automatically on Ubuntu). Test that it's running by connecting via the **sqlcmd** sql server command line tool
* Open the TestMsSQLGlobalLocker class in the test package of the locker module and into the mssql subtest module
* Put in the credentials for your connected instance in the setUp method
* That's it, you can now just run the tests normally and they should pass. all three

## License

Kill Bill commons is released under the [Apache license](http://www.apache.org/licenses/LICENSE-2.0).
