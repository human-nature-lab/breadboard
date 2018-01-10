import confirmTemplate from './confirm.template.html';
import './confirm.sass';

AlertService.$inject = ['$q', '$modal'];
export default function AlertService($q, $modal){
  this.confirm = function (prompt, title){
    const d = $q.defer();

    // This will need to be changed when future versions of ui.bootstrap are used
    let confirmModal = $modal.open({
      animation: true,
      templateUrl: confirmTemplate,
      resolve: {
        title: function() {
          return title ? title : "Confirm";
        },
        prompt: function(){
          return prompt;
        }
      },
      controller: ['$scope', 'prompt', 'title', function($scope, prompt, title){
        $scope.prompt = prompt;
        $scope.title = title;
        $scope.confirm = function(){
          confirmModal.close();
          d.resolve();
        };
        $scope.cancel = function(){
          confirmModal.dismiss("cancel");
          d.reject();
        };
      }]
    });
    return d.promise;
  }
}