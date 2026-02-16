# Contributing
There are many different ways to contribute to the Breadboard project. We welcome issues, code, 
documentation and examples. 

## Development

### Environment setup
Breadboard works with Java 7+. Make sure the Java SDK is installed before beginning development. 
Also, [SBT 0.13.x](https://github.com/sbt/sbt/releases/tag/v0.13.17) must be used in development for
now. Add a .env file to set environment variables (AMT keys, JAVA_HOME, etc) and start with the 
`./start-dev.sh` script. If using multiple Java installations, make sure you point JAVA_HOME to 1.8 
or 1.7.

- `cd frontend`
- `npm install`

### Running
#### Backend development
If modifications will be made to files in the **frontend** directory, use the frontend development 
instructions instead
- Start the play framework server using `sbt "run -Dconfig.file=conf/application-prod.conf"`

#### Frontend development
This uses a slightly different configuration to allow hot module replacement via webpack on frontend
files.
- Start the webpack server using `cd frontend && npm start`
- Start the play framework server using `./start-dev.sh`

## Production

### Compile jars
- `cd frontend && npm run build` to build frontend assets if this code has changed
- `sh create_prod_dist.sh` to compile distributable files
- In many cases, only copying the compiled **breadboard.jar** file is enough to update existing 
  Breadboard applications.