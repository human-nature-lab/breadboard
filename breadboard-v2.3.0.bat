java -cp "./lib/*;" -Dhttps.port=9443 -Dapplication.secret="pleasechangethis" -Dconfig.file=conf\application-prod.conf play.core.server.NettyServer
