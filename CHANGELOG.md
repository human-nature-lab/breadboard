# breadboard v2 change log

## [2.2.0] - 2015-12-02
### Added 
- A Client Graph dialog that allows users to modify the players' graph view without modifying source code
- Now hashing and salting admin passwords with BCrypt
- breadboard with an empty Users table prompts user to add first user 
### Fixed 
- Added proper syntax highlighting to the Client HTML dialog

## [2.1.0] - 2015-11-05
### Added 
- A Client HTML dialog that allows users to modify the players' client view without modifying source code
### Changed
- The admin graph function name from 'graph' to 'Graph' to match javascript conventions
- Moved TimersCtrl to its own client-timer.js file
 
## [2.0.0] - 2015-10-29
### Added
- The initial release version of breadboard v2

## A note on version numbers:
The version number will be incremented based on the following system, given a version number vX.Y.Z (e.g. v2.1.0): when
X (major) is incremented it means a new codebase that is not backward compatible with other major versions, when Y 
(minor) is incremented it means new features have been added that may require database evolutions but are compatible 
with databases created with software with the same major version number, and when Z (patch) is incremented it means
 that bugs with the features of the minor version have been fixed.

