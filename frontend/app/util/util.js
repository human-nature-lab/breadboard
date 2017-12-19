const util = {
  buildQueryStringParameters: buildQueryStringParameters
};

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

export default util;
