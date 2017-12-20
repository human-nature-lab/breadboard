ScriptInjectorService.$inject = ['$q', '$timeout'];
export default function ScriptInjectorService($q, $timeout){

  function makeScriptTag(props={}){
    let script = document.createElement('script');
    script.src = url;
    for(let opt in props){
      script[opt] = props[opt];
    }
    document.body.appendChild(script);
    return script;
  }

  this.injectScriptFromUrl = function(url, options){
    let deferred = $q.defer();

    let script = makeScriptTag(options.props);

    // Wait 5 seconds and then assume the request failed
    let timeoutPromise = $timeout(function(){
      deferred.reject("Unable to load resource from " + url);
    }, 5000);

    script.addEventListener('load', function(){
      $timeout.cancel(timeoutPromise);
      deferred.resolve();
    });

    return deferred.promise;
  };

  this.injectScript = function(contents){

    let geval = eval; // Hacky thing to evaluate script in global scope
    let timeoutPromise = $timeout(function(){
      geval(contents);
    });

    return $q.when(timeoutPromise);

  };

}