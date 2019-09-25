import 'jquery';
import 'bootstrap';
export default {
  dialog: {
    closeOnEscape: false,
      width: 500,
      height: 300,
      open: function(event, ui){
        // If there are any CodeMirror children of the dialog, refresh CodeMirror
        window.setTimeout(function() {
          var tab = event.target;
          var byClass = tab.getElementsByClassName("CodeMirror");
          for (var i = 0; i < byClass.length; i++) {
            var cm = byClass[i].CodeMirror;
            if (cm) {
              cm.refresh();
            }
          }
        }, 100);
      $(this.linkElem).addClass('open');
    },
    close: function(event, ui) {
      $(this.linkElem).removeClass('open');
    },
    create: function(event, ui) {
      let _this = this;
      let title = $(this).dialog('option', 'title');
      let link = $("<button type='button' class='dialog-link btn btn-default' role='button' aria-disabled='false'><span class='ui-button-text'>" + title + "</span></button>");
      $(link).click(function () {
        $(_this).dialog('open');
        $(_this).dialog('moveToTop');
        if (title == "Content") resizeTiny();
      });
      _this.linkElem = link;
      // $(_this).dialog('open');
      $('#dockDiv').append(link);

      if($(this).dialog("option", "autoOpen")){
        $(this).dialog('open');
      }
    }
  }
}