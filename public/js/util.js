'use strict';

if (!String.prototype.trim) {
  String.prototype.trim = function () {
    return this.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, '');
  };
}

function buildQueryStringParameters(parameters) {
  var returnQueryString = "";

  for (var key in parameters) {
    var value = parameters[key];
    if (value !== null && value !== undefined) {
      returnQueryString += encodeURIComponent(key) + "=" + encodeURIComponent(value) + "&";
    }
  }

  if (returnQueryString.length > 0) {
    // Remove trailing &
    returnQueryString = returnQueryString.substring(0, returnQueryString.length - 1);
    returnQueryString = "?" + returnQueryString;
  }

  return returnQueryString;
}
