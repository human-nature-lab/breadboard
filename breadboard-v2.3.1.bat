@ECHO OFF &SETLOCAL
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -fullversion 2^>^&1') do set "jver=%%j%%k%%l%%m"
set "n=0123456789%jver%"
set "n=%n:~-10,1%"
set x=10
set j9=1
set /a n=n - 2
for /L %%i in (1,1,%n%) do set /a j9*=x
set /a j9=j9*19
rem Set java options for jdk9 and above
if %jver% GTR %j9% (set _JAVA_OPTIONS="--add-modules=java.xml.bind")
rem run the breadboard server
java -cp "./lib/*;" -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf\application-prod.conf -Xms1024m -Xmx1024m -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=128m -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC play.core.server.NettyServer