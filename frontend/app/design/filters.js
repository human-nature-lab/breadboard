/* Filters */

angular.module('breadboard.filters', [])
    .filter('to_trusted', ['$sce', function($sce) {
        return function(text) {
            return $sce.trustAsHtml(text);
        };
    }])
    .filter('asNumber', asNumber)
    .filter('shortId', shortId)
    .filter('playerOrder', ['$filter', function($filter){
      return function(players){
        const ignoredPrefixes = ['_'];
        players.sort(function(a, b){
          let comp = a.id.localeCompare(b.id);
          let aContains = false;
          let bContains = false;
          for(let p of ignoredPrefixes){
            aContains = a.id.substr(0, p.length) === p;
            bContains = b.id.substr(0, p.length) === p;
            if((aContains || bContains) && !(aContains && bContains)) {
              comp = aContains ? 1 : -1;
            }
          }
          return comp;
        });
        return players;
      }
    }]);

function asNumber() {
  return function(input) {
    return Number(input);
  };
}

function shortId() {
  return function(input) {
    let maxLength = 10;
    if ((typeof input === 'string' || input instanceof String) && input.length > maxLength) {
      return input.substr(0, maxLength) + "...";
    }
    return input;
  };
}
