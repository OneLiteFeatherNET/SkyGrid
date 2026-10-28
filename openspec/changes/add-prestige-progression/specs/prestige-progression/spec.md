# Spec Delta

## Purpose

Lets players who have finished a challenge's goals opt into a harder, admin-bounded prestige step of
that challenge, with every change visible before and during play, and never derives rewards,
difficulty or steps from behavior signals.

## ADDED Requirements

### Requirement: Current and highest prestige step per player, challenge and season
The system SHALL keep, per player, season and challenge, a current prestige step where step 0 is the
unmodified challenge and the default for every player. The system SHALL keep the highest step ever
reached as an all-time record.

#### Scenario: New player starts at step 0
- **WHEN** a player without prestige records in the active season is asked for the step of a challenge
- **THEN** the current step is 0 and the highest step is 0

#### Scenario: Highest step survives a lower
- **WHEN** a player at current step 2 with highest step 2 lowers the step
- **THEN** the current step is 1 and the highest step is still 2

### Requirement: Raise preview requires confirmation
When a player runs `/skygrid prestige up <challenge>`, the system SHALL show the next step's complete
modifier list and state that the tasks of that step start fresh, together with a confirm action. The
system MUST NOT change the current step before the player confirms.

#### Scenario: Preview does not change the step
- **WHEN** a player runs `/skygrid prestige up one_life` without confirming
- **THEN** the preview lists every modifier of the next step and states that its tasks start fresh
- **AND** the current step is unchanged

#### Scenario: Confirmed raise changes the step
- **WHEN** a player runs `/skygrid prestige up one_life confirm` and all tasks are completed at the current step
- **THEN** the current step increases by exactly one

### Requirement: Raise by one step when all tasks are complete
When a player confirms a raise and every configured task of the challenge is completed at the current
step, the system SHALL raise the current step by exactly one and SHALL update the highest step if the
new current step exceeds it.

#### Scenario: Raise updates highest step
- **WHEN** a player at current step 1 with highest step 1 confirms a raise with all tasks complete at step 1
- **THEN** the current step is 2 and the highest step is 2

### Requirement: Raise is rejected with missing tasks
If a player has an incomplete task at the current step, then the system SHALL reject the raise and
name the missing tasks. The current step MUST remain unchanged.

#### Scenario: Missing task blocks raise
- **WHEN** a player confirms a raise while one task is incomplete at the current step
- **THEN** the raise is rejected with a message naming that task
- **AND** the current step is unchanged

### Requirement: Raise is rejected at the maximum step
If the current step is the last configured step of the challenge's ladder, then the system SHALL
reject the raise and state that the maximum step is reached.

#### Scenario: Raise at maximum
- **WHEN** a player at the last configured step confirms a raise
- **THEN** the raise is rejected with a message stating that the maximum is reached

### Requirement: Raise is rejected while the challenge is running
While the challenge is active, the system SHALL reject a raise for that challenge and state that the
change is possible between rounds.

#### Scenario: Raise during a round
- **WHEN** a player confirms a raise while the challenge is active
- **THEN** the raise is rejected with a message stating that the change is possible between rounds

### Requirement: Lowering is free at any time
When a player runs `/skygrid prestige down <challenge>`, the system SHALL lower the current step by one
at any time, including during a round, without confirmation. The system MUST NOT change the highest
step or any recorded task completion when lowering.

#### Scenario: Lower during a round
- **WHEN** a player at current step 2 runs `/skygrid prestige down` while the challenge is active
- **THEN** the current step becomes 1 without a confirmation prompt
- **AND** the highest step and all recorded task completions are unchanged

#### Scenario: Lower at step 0 is rejected
- **WHEN** a player at current step 0 runs `/skygrid prestige down`
- **THEN** the system reports that the player is already at the minimum step and nothing changes

### Requirement: Modifiers apply to the player at a prestige step
While a player's current step for an active challenge is above 0 and the player is in a SkyGrid world,
the system SHALL apply exactly that step's configured modifiers to that player and to no other player.

#### Scenario: Modifiers apply only to the stepped player
- **WHEN** player A is at step 2 and player B is at step 0 in the same active challenge
- **THEN** only player A receives the modifiers of step 2
- **AND** player B plays the unmodified challenge

#### Scenario: Modifiers stop outside a SkyGrid world
- **WHEN** a player at step 1 leaves the SkyGrid world
- **THEN** the step 1 modifiers no longer apply to that player

### Requirement: Task completion is recorded per prestige step
The system SHALL record task completion per progress scope, consisting of season, player, challenge and
prestige step, and per task. A task completed at step N SHALL remain completed at step N after any
raise or lower.

#### Scenario: Completion survives a lower and raise
- **WHEN** a player completes task T at step 2, lowers to step 1, and raises back to step 2
- **THEN** task T is still completed at step 2

#### Scenario: Completion at another step is independent
- **WHEN** a player completed task T at step 1 and is now at step 2
- **THEN** task T is not completed at step 2

### Requirement: Rewards are granted at most once per step
The system SHALL grant a task's configured reward at most once per player, challenge, task and prestige
step. The reward definition SHALL be the same at every step.

#### Scenario: Re-completing at a visited step grants nothing
- **WHEN** a player who received the reward for task T at step 1 lowers to step 0, completes T again and the reward was already granted at step 1
- **THEN** the reward for task T at step 0 is granted only if it was not granted at step 0 before

#### Scenario: Same reward at every step
- **WHEN** a player completes task T for the first time at step 2
- **THEN** the reward granted equals the reward configured for task T, the same as at step 0

### Requirement: No reward, modifier or step derives from behavior signals
The system MUST NOT derive a reward, its magnitude, a modifier or a prestige step from session length,
completion ratio, drop-off point, return rate, deaths or any other struggle or disengagement signal.
Behavior signals MUST NOT be collected for prestige.

#### Scenario: Deaths do not change the step
- **WHEN** a player at step 1 dies several times during an active challenge
- **THEN** the current step, the modifiers and the rewards are unchanged

#### Scenario: No automatic step change
- **WHEN** a player's session length or completion ratio changes
- **THEN** the current step does not change without a player command

### Requirement: Challenge sidebar shows the prestige step
While a player's current step for the first active challenge is above 0, the challenge sidebar SHALL
show the step number and a one-line modifier summary. The summary SHALL describe each modifier with
the same text used in the preview.

#### Scenario: Sidebar at step 0
- **WHEN** a player at step 0 views the challenge sidebar
- **THEN** no step number or modifier summary is shown

#### Scenario: Sidebar at step 2
- **WHEN** a player at step 2 views the challenge sidebar
- **THEN** the step number 2 and a one-line modifier summary are shown

### Requirement: Join notice for an active stepped challenge
When a player with a current step above 0 joins while that challenge is active, the system SHALL send
one chat line naming the step and its modifiers.

#### Scenario: Join while active
- **WHEN** a player at step 1 joins while the challenge is active
- **THEN** exactly one chat line names step 1 and its modifiers

#### Scenario: Join while inactive
- **WHEN** a player at step 1 joins while the challenge is inactive
- **THEN** no prestige chat line is sent

### Requirement: Prestige info command
When a player runs `/skygrid prestige info [challenge]`, the system SHALL list the current step, the
highest step, the maximum step, the current modifiers, the next step's modifiers, and whether the raise
is unlocked. Without a challenge argument, the system SHALL list one line per challenge with a ladder.

#### Scenario: Info for one challenge
- **WHEN** a player runs `/skygrid prestige info one_life`
- **THEN** the output contains the current step, highest step, maximum step, current modifiers, next step modifiers and the unlock state

#### Scenario: Info without argument
- **WHEN** a player runs `/skygrid prestige info` on a server with two challenges that have ladders
- **THEN** one line is listed for each of the two challenges

### Requirement: Translated player text
All player-facing prestige text SHALL be translatable and SHALL be shipped in English and German.

#### Scenario: German locale
- **WHEN** a player whose client language is German runs `/skygrid prestige info`
- **THEN** the output uses the German message bundle

### Requirement: Optional unlock notice
When a player completes the last open task at the current step and a next step exists, the system SHALL
send one neutral chat line that prestige is available. The line SHALL be sent at most once per player,
challenge and step, and SHALL NOT be repeated on later joins.

#### Scenario: Unlock notice once
- **WHEN** a player completes the last open task at step 0 and step 1 exists
- **THEN** one neutral chat line announces that prestige is available
- **AND** no further unlock line is sent for step 0 on later joins

#### Scenario: Notice disabled by admin
- **WHEN** the challenge sets `prestige.announceUnlock` to false and a player completes the last open task at the current step
- **THEN** no unlock line is sent

### Requirement: Admin prestige ladder configuration
The system SHALL read an optional `prestige` block from `challenges/<id>.<ext>` in JSON, YAML and TOML,
and SHALL include that block in the generated challenge schema.

#### Scenario: Valid ladder loads
- **WHEN** a challenge file contains a valid `prestige` block with steps 1 to 3
- **THEN** the challenge is loaded with that ladder
- **AND** the generated challenge schema contains the `prestige` property

### Requirement: Invalid ladder disables prestige for one challenge only
If a ladder has non-contiguous step numbers, more than 10 steps, a value outside a modifier's hard
bounds, a step that is not strictly harder than the previous one, or a modifier type the challenge
behavior does not support, then the system SHALL disable prestige for that challenge, log a warning
naming the file and the offending step, and SHALL keep loading the other challenges.

#### Scenario: Out of bounds value
- **WHEN** a challenge file sets `max_health` to 21 half-hearts in step 1
- **THEN** prestige is disabled for that challenge
- **AND** a warning names the file and step 1
- **AND** the other challenges load normally

#### Scenario: Non-contiguous steps
- **WHEN** a ladder defines steps 1 and 3 but not 2
- **THEN** prestige is disabled for that challenge with a logged warning

#### Scenario: Unsupported modifier type
- **WHEN** a ladder for `level_is_border` uses a modifier type that the challenge behavior does not support
- **THEN** prestige is disabled for that challenge with a logged warning

### Requirement: Admin inspection of a player's steps
When an admin runs `/skygrid prestige of <player> [challenge]`, the system SHALL show that player's
current and highest steps for the given challenge, or for every challenge with a ladder when no
challenge is given.

#### Scenario: Admin views a player
- **WHEN** an admin runs `/skygrid prestige of Alex one_life`
- **THEN** the output shows Alex's current and highest step for `one_life`

#### Scenario: Non-admin is refused
- **WHEN** a player without the admin permission runs `/skygrid prestige of Alex`
- **THEN** the command is refused and nothing is shown

### Requirement: Admin season reset with confirmation
When an admin runs `/skygrid prestige reset`, the system SHALL only show what will be lost. When an admin
runs `/skygrid prestige reset confirm`, the system SHALL reset every player's current step to 0 in the
active season and SHALL keep the all-time highest record.

#### Scenario: Reset without confirmation changes nothing
- **WHEN** an admin runs `/skygrid prestige reset`
- **THEN** the output describes the affected steps
- **AND** no current step changes

#### Scenario: Confirmed reset keeps records
- **WHEN** an admin runs `/skygrid prestige reset confirm`
- **THEN** every player's current step in the active season is 0
- **AND** every player's highest step is unchanged

### Requirement: Challenges without a ladder
Where a challenge has no `prestige` block, the system SHALL report it as "no prestige" in
`/skygrid prestige info`, and tab completion SHALL NOT offer that challenge for `up` or `down`.

#### Scenario: Challenge without ladder
- **WHEN** a player runs `/skygrid prestige info no_crafting_table` and that challenge has no ladder
- **THEN** the output reports "no prestige"

#### Scenario: Tab completion omits challenge
- **WHEN** a player types `/skygrid prestige up` and presses tab
- **THEN** only challenges with a ladder are suggested
