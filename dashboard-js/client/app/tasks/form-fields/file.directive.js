'use strict';
angular.module('dashboardJsApp').directive('fileField', function($modal, $http, generationService, Modal, ScannerService) {
  return {
    require: 'ngModel',
    restrict: 'E',
    link: function(scope, element, attrs, ngModel) {
      var fileField = element.find('input');

      fileField.bind('change', function(event) {
        scope.$apply(function() {
          scope.upload(event.target.files, attrs.name);
        });
      });

      fileField.bind('click', function(e) {
        e.stopPropagation();
      });
      element.find('#upload-button').bind('click', function(e) {
        e.preventDefault();
        fileField[0].click();
      });



      scope.openScanModal = function (item) {
        $http.get(ScannerService.getTwainServerUrl()).success(function (data) {
          if(data){
            scanDocument();
          }
        }).error(function (err) {
          Modal.inform.error()('Сталася помилка при намаганні перевірити підключення до служби TWAIN@Web: ' + JSON.toString(err));
        });

      };

      function scanDocument() {
        var modalInstance = $modal.open({
          animation: true,
          templateUrl: 'components/scanner/scan-modal.html',
          controller: 'ScannerModalCtrl',
          resolve: {}
        });

        modalInstance.result.then(function (oScanResult) {
          var aFiles = [];
          var base64content = oScanResult.downloadFiles["0"].base64;

          var oScannedFile = generationService.getFileFromBase64(base64content, oScanResult.downloadFiles["0"].file, 'image/jpeg');

          aFiles.push(oScannedFile);
          scope.upload(aFiles, attrs.name);

        });
      }
    },
    templateUrl: 'app/tasks/form-fields/file-field.html',
    replace: true,
    transclude: true
  };
});
