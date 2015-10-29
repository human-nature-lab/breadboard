// Source:  http://stackoverflow.com/questions/6312993/javascript-seconds-to-time-with-format-hhmmss 
String.prototype.toHHMMSS = function () {
    var ms_num = parseInt(this, 10); // don't forget the second parm
    if (ms_num < 0) return "00:00";
    var sec_num = Math.round(ms_num/1000); // don't forget the second parm
    var hours   = Math.floor(sec_num / 3600);
    var minutes = Math.floor((sec_num - (hours * 3600)) / 60);
    var seconds = sec_num - (hours * 3600) - (minutes * 60);

    if (hours   < 10) {hours   = "0"+hours;}
    if (minutes < 10) {minutes = "0"+minutes;}
    if (seconds < 10) {seconds = "0"+seconds;}
    var time = minutes + ':' + seconds;
    if (hours > 0) time = hours + ':' + time;
    return time;
};


