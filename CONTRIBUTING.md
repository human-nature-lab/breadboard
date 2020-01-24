# Contributing
There are many different ways to contribute to the Breadboard project. We welcome issues, code, documentation
and examples. 

## Development

### Environment setup
Breadboard works with Java 7+. Make sure the Java SDK is installed before beginning development.

- `cd frontend`
- `npm install`

### Running
#### Backend development
If modifications will be made to files in the **frontend** directory, use the frontend development instructions instead
- Start the play framework server using `sbt "run -Dconfig.file=conf/application-prod.conf"`

#### Frontend development
This uses a slightly different configuration to allow hot module replacement via webpack on frontend files.
- Start the webpack server using `cd frontend && npm start`
- Start the play framework server using `sbt "run -Dconfig.file=conf/application-dev.conf"`


From a terminal run . This will start a dev server which will
automatically rebuild the frontend files whenever a file changes.
