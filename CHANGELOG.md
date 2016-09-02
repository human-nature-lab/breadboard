### [v2.2.4] - 2016-07-05 
#### Added 
- Added support for html5 'required' attribute in HTML forms added to custom property of public actions
- Added LICENSE file to download
#### Changed 
- Changed 'Disallow previous players?' options in the AMT dialog to be less confusing
### Fixed 
- Resolved 'Not a valid Play application' error with Windows breadboard.bat batch file
- Fixed issue with recording connections in data when using the Watts-Strogatz graph
- Changed default Client HTML to support required input elements added using the custom property of the a.add method
- Resolved 'No such Property: onLeaveStep' error when launching new experiment instance with nodes in the graph
- Fixed bug where multiple instances with the same name will be highlighted in the Launch dialog if one is RUNNING
- Fixed an error where edge properties are no longer displayed in the graph dialog after browser refresh

### [v2.2.3] - 2016-04-01
#### First public release!
#### Added 
- Now can launch on Windows PCs using breadboard.bat 
- Settings can now be modified using the application-prod.conf file 

### [v2.2.2] - 2016-03-09
#### Fixed 
- Fixed custom PlayerActions with regards to checkboxes

### [v2.2.1] - 2016-03-01
#### Added
- The wattsStrogatz graph algorithm implementing the Watts-Strogatz small world algorithm
- A edge.randV() function that returns a random vertex attached to the edge
- Settings can now be set using an application-prod.conf file in the root of the breadboard directory

#### Changed
- Removed the confusing 'Extend HIT' and 'Assign Qualification' buttons from the AMT Assignments dialog
- Updated the README

#### Fixed 
- Fixed support for checkboxes and radio buttons as input options for custom player choices

### [v2.2.0] - 2015-12-02
#### Added 
- A Client Graph dialog that allows users to modify the players' graph view without modifying source code
- Now hashing and salting admin passwords with BCrypt
- breadboard with an empty Users table prompts user to add first user 

#### Fixed 
- Added proper syntax highlighting to the Client HTML dialog

### [v2.1.0] - 2015-11-05
#### Added 
- A Client HTML dialog that allows users to modify the players' client view without modifying source code

#### Changed
- The admin graph function name from 'graph' to 'Graph' to match javascript conventions
- Moved TimersCtrl to its own client-timer.js file
 
### [v2.0.0] - 2015-10-29
#### Added
- The initial release version of breadboard v2

### A note on version numbers:
The version number will be incremented based on the following system, given a version number vX.Y.Z (e.g. v2.1.0): when
X (major) is incremented it means a new codebase that is not backward compatible with other major versions, when Y 
(minor) is incremented it means new features have been added that may require database evolutions but are compatible 
with databases created with software with the same major version number, and when Z (patch) is incremented it means
 that bugs with the features of the minor version have been fixed.
