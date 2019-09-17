import 'core-js'
import 'regenerator-runtime'
import { Breadboard } from './breadboard'
window.Breadboard = new Breadboard()
import 'jquery';
import angular from 'angular';
import '../lib/jquery-ui/jquery-ui';
import 'angular-ui-router';
import 'angular-route';
import 'angular-cookies';
import 'angular-sanitize';
import '../lib/angular-ui/angular-ui';
import '../lib/angular-ui/ui-utils';
import '../lib/angular-ui/tinymce';
import '../lib/angular-ui/ui-codemirror';
import codemirror from 'codemirror/lib/codemirror';
window.CodeMirror = codemirror;
import 'codemirror/mode/groovy/groovy';
import 'codemirror/mode/css/css';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/mode/xml/xml';
import 'codemirror/mode/vbscript/vbscript';
import 'codemirror/mode/htmlmixed/htmlmixed';
import 'codemirror/keymap/vim';
import 'codemirror/keymap/sublime';
import 'codemirror/keymap/emacs';
import './design/app';
