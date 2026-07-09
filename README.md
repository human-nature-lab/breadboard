README - breadboard v2 
=====================================

*If you have any questions about breadboard, or want to discuss how you're using breadboard for your project, head over to GitHub Discussions by clicking the "Discussions" tab above or going to: [https://github.com/human-nature-lab/breadboard/discussions](https://github.com/human-nature-lab/breadboard/discussions]).*

breadboard is a software platform for developing and conducting human interaction experiments on networks. 

It allows researchers to rapidly design experiments using a flexible domain-specific language and provides researchers with immediate access to a diverse pool of online participants.

Features:

* Experiment logic is expressed in a graph traversal DSL 
* Experiment content is stored in a content management system and edited using a WYSIWYG editor 
* Real-time graph visualization during experiment design and deployment
* High performance bi-directional client-server communication using Netty and WebSockets
* An interactive script window allows the experimenter to quickly make changes to the graph and experiment
* Recruit online participants from Amazon Mechanical Turk using the integrated module

breadboard is built using:

* [The Play Framework](https://www.playframework.com/)
* [Groovy](http://www.groovy-lang.org/) 
* [TinkerPop's](http://tinkerpop.incubator.apache.org/) open source graph computing framework  
* [AngularJS](https://angularjs.org/)
* [D3.js](http://d3js.org/)
* [TinyMCE](http://www.tinymce.com/)
* [CodeMirror](https://codemirror.net/)

Also [Apache Commons](https://commons.apache.org/), [imgscalr](https://github.com/thebuzzmedia/imgscalr), [JUNG](http://jung.sourceforge.net/), [jQuery](https://jquery.com/), [Modernizr](https://modernizr.com/), [Underscore](http://underscorejs.org/), and [Bootstrap](http://getbootstrap.com/).

### Running breadboard

Release builds are **self-contained** — they bundle a Java 8 runtime (Amazon Corretto 8), so you do
**not** need Java installed. Download the zip for your platform, unzip it, and run the launcher:

| Platform | Download | Run |
| --- | --- | --- |
| Linux (x64) | `breadboard-<ver>-linux-x64.zip` | `./run.sh` |
| Windows (x64) | `breadboard-<ver>-windows-x64.zip` | `run.bat` |
| macOS (Intel) | `breadboard-<ver>-mac-x64.zip` | `./run.sh` |
| macOS (Apple Silicon) | `breadboard-<ver>-mac-aarch64.zip` | `./run.sh` |

> **macOS:** the bundled runtime is unsigned, so Gatekeeper may quarantine it after download. If
> you see "cannot be opened", clear the attribute once: `xattr -dr com.apple.quarantine breadboard-<ver>/`.

The launcher listens on http://localhost:9000 by default. Provide secrets via the environment
(read by `conf/application-prod.conf`) and pass extra JVM/Play options through to the launcher:

```sh
APPLICATION_SECRET="$(openssl rand -hex 32)" ./run.sh -Dhttps.port=9443 -Dhttps.keyStore=conf/prod.keystore
```

#### Docker

A container image based on `amazoncorretto:8` is published on tagged releases:

```sh
docker run -p 9000:9000 \
  -e APPLICATION_SECRET="$(openssl rand -hex 32)" \
  -v breadboard-db:/opt/breadboard/db \
  ghcr.io/human-nature-lab/breadboard:<ver>
```

### Contributing
See the [contributing guide](CONTRIBUTING.md)
