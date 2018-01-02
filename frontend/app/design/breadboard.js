'use strict';

window.resizeTiny = function () {
  if (!tinyMCE) return;
  var contentDiv = document.getElementById("contentDiv");
  var contentTabsDiv = document.getElementById("contentTabsDiv");
  var mceuToolbar = document.getElementsByClassName("mce-toolbar-grp")[0];
  if (contentDiv && contentTabsDiv && mceuToolbar) {
    if (!tinyMCE.get("tinymceTextarea")) return;
    //alert (mceuToolbar.clientHeight);
    window.setTimeout(function () {
      tinyMCE.get("tinymceTextarea").theme.resizeTo(
        contentDiv.clientWidth - 20,
        contentDiv.clientHeight - (contentTabsDiv.clientHeight + mceuToolbar.clientHeight)
      );
    }, 50);
  }
};

$(document).on('dialogresizestop', '.contentDialog', resizeTiny);

// Function to dock dialogs at the bottom of the window
window.dockWindow = function (dialog, title) {
  var link = $("<button type='button' class='ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only' role='button' aria-disabled='false' id='closed-dialog-" + title + "'><span class='ui-button-text'>" + title + "</span></button>");
  $(link).click(function () {
    $(dialog).dialog('open');
    if (title == "Content") resizeTiny();
    $(this).remove();
  });
  $('#dockDiv').append(link);
};

