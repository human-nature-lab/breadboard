DownloadService.$inject = ['$http', '$q'];
export default function DownloadService($http, $q){

  this.getFile = function(url){
    return $http.get(url, {
      responseType: 'arraybuffer'
    }).then(function(res){
      if(res.status === 401){
        throw Error('unauthorized to download files');
      } else if(res.status !== 200){
        throw Error(res.status);
      }
      return res;
    });
  };

  this.download = function(url, fileName){
    return this.getFile(url)
      .then(res => {
        return this.saveAsBlob(res.data, fileName);
      });
  };

  this.saveAsBlob = function(data, fileName){
    const dataURI = URL.createObjectURL(new Blob([data]));
    return this.saveAs(dataURI, fileName);
  };

  this.saveAs = function(dataURI, fileName){
    return $q((resolve, reject) => {
      try {
        const a = document.createElement('a');
        a.href = dataURI;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        resolve("Downloading " + fileName);
      } catch(e){
        reject(e);
      }
    })
  };

}