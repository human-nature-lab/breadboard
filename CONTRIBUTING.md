# Contributing
There are many different ways to contribute to the Breadboard open-source project. We welcome issues, code, documentation
and examples. 

## Development

### Environment setup
Breadboard works with Java 7+. Make sure this SDK is installed before starting development.

- `cd frontend`
- `npm install`

#### Windows

From a terminal run `sbt "run -Dconfig.file=conf/application-dev.conf"`. This will start a dev server which will
automatically rebuild the frontend files whenever a file changes.
