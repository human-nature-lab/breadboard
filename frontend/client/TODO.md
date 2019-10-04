# TODO
- [ ] Locales
- [ ] Gremlins
- [ ] Graph API
    - [ ] Handle node and edge updates with a diff first
    - [x] Basic graph diffing
- [ ] SVGGraph
    - [ ] debounce graph updates to avoid multiple, rapid resets of the force-directed graph simulation
    - [ ] handle input passing
    - [ ] optional centering of the ego node
    - [ ] filter ignored props from binding to SVG
- [ ] Component slots
    - [ ] Timer label
    - [ ] Button contents
- [ ] Documentation
    - [ ] SVGGraph properties
    - [ ] Events
        - [ ] Breadboard events
        - [ ] Graph events
    - [ ] Examples
        - [ ] Most basic example of frontend use
        - [ ] Mapping props (fill, stroke, strokeWidth) using map functions
        - [ ] Using slots to customize existing components
        - [ ] Loading an external library to use
    - [ ] Tutorials
        - [ ] How to use a completely separate UI (phaser?)
        - [ ] Overriding a Vue component to customize something (buttons)
- [x] Choices
  - [x] Custom choices
- [x] Player text
- [x] Custom styles
- [x] Timer


# Flow
- Breadboard object loads
- Client JS and templates load
- Client JS creates the Vue instance (or whatever)
