# @human-nature-lab/breadboard-client
This package contains the Vue 2 components for the Breadboard client.

## Install
```bash
npm i --save @human-nature-lab/breadboard-client
```

### Usage
```typescript
import { PlayerTimers } from '@human-nature-lab/breadboard-client'
```

## Releasing
1. Increment version: `npm version {major|minor|patch|prerelease}`
2. Publish: `pnpm publish` and follow prompts (pnpm rewrites the `workspace:*` core dependency to a real version range — `npm publish` would ship the literal `workspace:*` and break installs)
3. Push changes: `git push && git push --tags`
