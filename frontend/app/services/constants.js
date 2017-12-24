export default angular.module('breadboard.constants', [])
  .constant('STATUS', {
    'UNLOADED' : 0,
    'LOADING' : 1,
    'UNCHANGED' : 2,
    'MODIFIED' : 3,
    'SAVING' : 4,
    'SAVED' : 5,
    'ERROR' : 6
  });
