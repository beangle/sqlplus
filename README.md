# Database Utility
Beangle Database Development Utility

### Transport data from db1 to db2

Edit config file(oracle to postgresql etc.)

    <?xml version="1.0" encoding="UTF-8"?>
    <transport maxthreads="10">
      <source>
        <driver>oracle</driver>
        <url>jdbc:oracle:thin:@//192.168.100.1:1521/public</url>
        <user>user</user>
        <password>password</password>
      </source>
      <target>
        <driver>postgresql</driver>
        <url>jdbc:postgresql://192.168.100.2:5432/urp</url>
        <user>user</user>
        <password>password</password>
      </target>

      <task from="user" to="user">
        <tables lowcase="true" index="true" constraint="true">
          <includes>*</includes>
          <excludes></excludes>
        </tables>
      </task>

      <actions>
         <before>
           <sql file="/path/to/sql/file/do/something/in/oracle.sql"/>
         </before>
         <after>
           <sql file="/path/to/sql/file/do/something/in/postgresql.sql"/>
         </after>
      </actions>
    </transport>

Download scripts

    wget https://raw.githubusercontent.com/beangle/db/main/src/main/scripts/transport.sh
    chmod +x transport.sh
    ./transport.sh /path/to/your.xml
