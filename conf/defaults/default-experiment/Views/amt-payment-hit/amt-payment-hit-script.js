/**
 * Gets a URL parameter from the query string
 */
function turkGetParam(name, defaultValue) {
  var regexS = "[\?&]" + name + "=([^&#]*)";
  var regex = new RegExp(regexS);
  var tmpURL = window.location.href;
  var results = regex.exec(tmpURL);
  if (results == null) {
    return defaultValue;
  } else {
    return results[1];
  }
}

/**
 * URL decode a parameter
 */
function decode(strToDecode) {
  var encoded = strToDecode;
  return unescape(encoded.replace(/\+/g, " "));
}

/**
 * Returns the Mechanical Turk Site to post the HIT to (sandbox. prod)
 */
function turkGetSubmitToHost() {
  return decode(turkGetParam("turkSubmitTo", "https://www.mturk.com"));
}

/**
 * Sets the assignment ID in the form. Defaults to use mturk_form and submitButton
 */
function turkSetAssignmentID(form_name, button_name) {

  if (form_name == null) {
    form_name = "mturk_form";
  }

  if (button_name == null) {
    button_name = "submitButton";
  }

  assignmentID = turkGetParam('assignmentId', "");
  document.getElementById('assignmentId').value = assignmentID;

  if (assignmentID == "ASSIGNMENT_ID_NOT_AVAILABLE") {
    // If we're previewing, disable the button and give it a helpful message
    btn = document.getElementById(button_name);
    if (btn) {
      btn.disabled = true;
      btn.value = "You must ACCEPT the HIT before you can submit the results.";
    }
  }

  form = document.getElementById(form_name);
  if (form) {
    form.action = turkGetSubmitToHost() + "/mturk/externalSubmit";
  }
}

window.onload = turkSetAssignmentID;
