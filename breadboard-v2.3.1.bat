@ECHO OFF &SETLOCAL
rem Parse the output of java --fullversion into an integer
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k%%l%%m"

rem Set java options for jdk9
if %jver% GTR 19000 (set _JAVA_OPTIONS="--add-modules=java.xml.bind")

rem run the breadboard server
java -cp "./lib/*;" -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf\application-prod.conf -Xms1024m -Xmx1024m -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=128m -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC play.core.server.NettyServer