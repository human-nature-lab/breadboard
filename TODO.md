# TODO

1. [ ] Add frontend layout to intercept waiting room and other built-in pages
       a. [ ] Add waiting room to frontend
       b. [ ] Add prolific exit to frontend
       c. [ ] Add MTurk exit to frontend
2. [ ] Add helper methods to stop game recruitment, but allow in progress games to continue. This would help standardize how this process happens
3. [ ] Add a hook when engine reloads `onBeforeEngineReload` so that resources can be cleaned up
4. [ ] Add a module to give some visibility into the waiting room and recruitment process
       a. [ ] Add a data registry that can be shared with the ScriptBoard and admin socket to view this state
       b. [ ] Add a frontend module that displays this state somehow
5. [ ] Show active SharedTimers in the admin view and allow them to be managed there
       a. [ ] Add to the players module
       b. [ ] Pause/Resume timer
       c. [ ] Change timer duration live
