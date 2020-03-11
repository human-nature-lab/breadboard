function svgTest() {
  return Modernizr.svg;
}

function inlineSvgTest() {
  return Modernizr.inlinesvg;
}

function websocketsTest() {
  return Modernizr.websockets;
}

async function loadImage(src) {
  return new Promise((resolve, reject) => {
    let img = new Image();
    img.onload = () => resolve();
    img.onerror = reject;
    img.src = src;
  });
}

async function speedTest() {
  const kb = 1467;
  const minSpeed = 300;
  let start = (new Date()).getTime();
  await loadImage("http://brdbrd.net/img/speedtest.jpg?random=" + Math.random());
  let s = ( (new Date()).getTime() - start ) / 1000;
  let kbps = Math.round(kb/s);
  return kbps >= minSpeed;
}
