ScriptInjectorService.$inject = ['$q', '$timeout'];
export default function ScriptInjectorService($q, $timeout){

  this.injectScript = function(url, options){
    let deferred = $q.defer();

    let script = document.createElement('script');
    script.src = url;
    for(let opt in options){
      script[opt] = options[opt];
    }
    document.body.appendChild(script);

    // Wait 10 seconds and then assume the request failed
    let timeoutId = $timeout(function(){
      deferred.reject("Unable to load resource from " + url);
    }, 5000);

    script.addEventListener('load', function(){
      $timeout.cancel(timeoutId);
      deferred.resolve();
    });

    return deferred.promise;
  };

}