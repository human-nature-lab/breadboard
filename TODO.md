# TODO

1. [x] Add frontend layout to intercept waiting room and other built-in pages
       a. [x] Add waiting room to frontend
       b. [x] Add prolific exit to frontend
       c. [x] Add MTurk exit to frontend
2. [ ] Add helper methods to stop game recruitment, but allow in progress games to continue. This would help standardize how this process happens
3. [ ] Add a hook when engine reloads `onBeforeEngineReload` so that resources can be cleaned up
4. [ ] Add a module to give some visibility into the waiting room and recruitment process
       a. [ ] Add a data registry that can be shared with the ScriptBoard and admin socket to view this state
       b. [ ] Add a frontend module that displays this state somehow
5. [ ] Show active SharedTimers in the admin view and allow them to be managed there
       a. [ ] Add to the players module
       b. [ ] Pause/Resume timer
       c. [ ] Change timer duration live
6. [ ] Games registry
       a. [ ] Automatically update state in the recruitment controller as much as possible
       b. [ ] Should accept multiple named game definitions
       c. [ ] Should launch games in separate threads if possible/feasible
7. [ ] Support arbitrary data files that are included in import/export and accessible to groovy code by default
