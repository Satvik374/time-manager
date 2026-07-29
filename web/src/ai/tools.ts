/*
 * The tool surface handed to the model. Names are stable identifiers — the settings screen stores
 * enabled tools by name, so renaming one silently disables it for existing users. Kept in step
 * with the Android app's AiTools.kt.
 */

export interface JsonSchema {
  type: string
  description?: string
  properties?: Record<string, JsonSchema>
  items?: JsonSchema
  enum?: string[]
  required?: string[]
}

export interface ToolSpec {
  name: string
  humanLabel: string
  description: string
  mutating: boolean
  parameters: JsonSchema
}

export const TOOL_NAMES = {
  GET_SCHEDULE: 'get_schedule',
  GET_TASK: 'get_task',
  SEARCH_TASKS: 'search_tasks',
  CREATE_TASK: 'create_task',
  UPDATE_TASK: 'update_task',
  DELETE_TASK: 'delete_task',
  MOVE_TASK: 'move_task',
  SET_TASK_COMPLETION: 'set_task_completion',
  ADD_CHECKLIST_ITEMS: 'add_checklist_items',
  UPDATE_CHECKLIST_ITEM: 'update_checklist_item',
  REMOVE_CHECKLIST_ITEM: 'remove_checklist_item',
  GET_ANALYTICS: 'get_analytics',
  GET_PROGRESS: 'get_progress',
  LIST_REWARDS: 'list_rewards',
  LIST_CATEGORIES: 'list_categories',
  SUGGEST_RECOMMENDATION: 'suggest_recommendation',
} as const

const DATE_HINT = 'Date as YYYY-MM-DD. Omit for today.'
const TIME_HINT = 'Start time as HH:MM in 24-hour local time, e.g. 07:30 or 19:05.'

function schema(properties: Record<string, JsonSchema>, required: string[] = []): JsonSchema {
  return { type: 'object', properties, required }
}

const str = (description: string, values?: string[]): JsonSchema =>
  values ? { type: 'string', description, enum: values } : { type: 'string', description }
const int = (description: string): JsonSchema => ({ type: 'integer', description })
const bool = (description: string): JsonSchema => ({ type: 'boolean', description })
const strArray = (description: string): JsonSchema => ({
  type: 'array',
  description,
  items: { type: 'string' },
})

export const ALL_TOOLS: ToolSpec[] = [
  {
    name: TOOL_NAMES.GET_SCHEDULE,
    humanLabel: 'Read the timetable',
    mutating: false,
    description:
      "Read the user's timetable for one or more days, including each block's id, time, category, " +
      'completion state and checklist items with their ids. Always call this before editing anything.',
    parameters: schema({
      date: str(DATE_HINT),
      days: int('How many consecutive days to return starting at `date`. Default 1, max 14.'),
    }),
  },
  {
    name: TOOL_NAMES.GET_TASK,
    humanLabel: 'Read one block',
    mutating: false,
    description: 'Fetch a single block by id with its full definition and checklist.',
    parameters: schema({ task_id: int("The block's numeric id.") }, ['task_id']),
  },
  {
    name: TOOL_NAMES.SEARCH_TASKS,
    humanLabel: 'Search blocks',
    mutating: false,
    description:
      'Find blocks whose title, note or category matches a query string. Use this when the user ' +
      'names a block but you do not know its id.',
    parameters: schema({ query: str("Free text, e.g. 'gym' or 'chemistry'.") }, ['query']),
  },
  {
    name: TOOL_NAMES.CREATE_TASK,
    humanLabel: 'Create a block',
    mutating: true,
    description:
      'Add a new block to the timetable. Either give `date` for a one-off block or `days` for a ' +
      "repeating one. Returns the new block's id.",
    parameters: schema(
      {
        title: str("Short name of the block, in the user's own words."),
        start_time: str(TIME_HINT),
        duration_minutes: int('Length in minutes, 5–720.'),
        date: str('For a one-off block: YYYY-MM-DD.'),
        days: strArray(
          "For a repeating block: any of mon,tue,wed,thu,fri,sat,sun, or the shorthands 'daily', " +
            "'weekdays', 'weekends'.",
        ),
        category: str('Grouping label such as Study, Work, Health, Personal.'),
        priority: str('Block importance.', ['low', 'normal', 'high']),
        note: str('Optional longer description.'),
        checklist: strArray('2–5 short imperative sub-steps to tick off.'),
        reminder_minutes: int('Notify this many minutes before the start. Omit for none.'),
        color: str('Hex colour like #6C5CE7. Optional.'),
      },
      ['title', 'start_time', 'duration_minutes'],
    ),
  },
  {
    name: TOOL_NAMES.UPDATE_TASK,
    humanLabel: 'Edit a block',
    mutating: true,
    description: 'Change fields on an existing block. Only the fields you pass are modified.',
    parameters: schema(
      {
        task_id: int("The block's numeric id."),
        title: str('New title.'),
        start_time: str(TIME_HINT),
        duration_minutes: int('New length in minutes.'),
        days: strArray('New repeat days, same vocabulary as create_task.'),
        date: str('New one-off date, YYYY-MM-DD.'),
        category: str('New category.'),
        priority: str('New priority.', ['low', 'normal', 'high']),
        note: str('New note text.'),
        reminder_minutes: int('New reminder lead time, or -1 to remove it.'),
        color: str('New hex colour.'),
        archived: bool('Set true to hide the block without deleting its history.'),
      },
      ['task_id'],
    ),
  },
  {
    name: TOOL_NAMES.DELETE_TASK,
    humanLabel: 'Delete a block',
    mutating: true,
    description:
      'Permanently delete a block and its history. Prefer archiving via update_task when the user ' +
      'only wants it off the schedule.',
    parameters: schema({ task_id: int("The block's numeric id.") }, ['task_id']),
  },
  {
    name: TOOL_NAMES.MOVE_TASK,
    humanLabel: 'Reschedule a block',
    mutating: true,
    description: 'Shorthand for moving a block to a new time, length and/or day.',
    parameters: schema(
      {
        task_id: int("The block's numeric id."),
        start_time: str(TIME_HINT),
        duration_minutes: int('New length in minutes.'),
        date: str('Move a one-off block to this date, YYYY-MM-DD.'),
      },
      ['task_id'],
    ),
  },
  {
    name: TOOL_NAMES.SET_TASK_COMPLETION,
    humanLabel: 'Tick a block off',
    mutating: true,
    description:
      'Mark a block done or not-done for a specific day. Only do this when the user says they did ' +
      'or did not do it.',
    parameters: schema(
      {
        task_id: int("The block's numeric id."),
        completed: bool('True to mark done, false to clear it.'),
        date: str(DATE_HINT),
      },
      ['task_id', 'completed'],
    ),
  },
  {
    name: TOOL_NAMES.ADD_CHECKLIST_ITEMS,
    humanLabel: 'Add checklist steps',
    mutating: true,
    description: 'Append checklist steps to a block. Items are short and imperative.',
    parameters: schema(
      {
        task_id: int("The block's numeric id."),
        items: strArray("Steps to append, e.g. ['Warm up', 'Sets 1-3', 'Stretch']."),
        replace: bool('True to replace the existing checklist instead of appending.'),
      },
      ['task_id', 'items'],
    ),
  },
  {
    name: TOOL_NAMES.UPDATE_CHECKLIST_ITEM,
    humanLabel: 'Edit or tick a checklist step',
    mutating: true,
    description:
      'Rename a checklist step and/or tick it for a given day. Item ids come from get_schedule or ' +
      'get_task.',
    parameters: schema(
      {
        item_id: int("The checklist item's numeric id."),
        text: str('New text for the step.'),
        checked: bool('Tick or untick the step for `date`.'),
        date: str(DATE_HINT),
      },
      ['item_id'],
    ),
  },
  {
    name: TOOL_NAMES.REMOVE_CHECKLIST_ITEM,
    humanLabel: 'Delete a checklist step',
    mutating: true,
    description: 'Remove a checklist step from its block.',
    parameters: schema({ item_id: int("The checklist item's numeric id.") }, ['item_id']),
  },
  {
    name: TOOL_NAMES.GET_ANALYTICS,
    humanLabel: 'Read analytics',
    mutating: false,
    description:
      'Completion stats for a period: totals, per-day breakdown, category split, best and worst days.',
    parameters: schema({
      period: str('Which window to report.', ['daily', 'weekly', 'monthly']),
      date: str('Any date inside the window, YYYY-MM-DD. Omit for today.'),
    }),
  },
  {
    name: TOOL_NAMES.GET_PROGRESS,
    humanLabel: 'Read streak and rewards',
    mutating: false,
    description:
      'Current streak, longest streak, freeze tokens, XP, level and coin balance, plus recently ' +
      'unlocked achievements.',
    parameters: schema({}),
  },
  {
    name: TOOL_NAMES.LIST_REWARDS,
    humanLabel: 'List the reward shop',
    mutating: false,
    description:
      'The rewards the user can spend coins on, with costs and affordability. You cannot redeem ' +
      'them — only the user can.',
    parameters: schema({}),
  },
  {
    name: TOOL_NAMES.LIST_CATEGORIES,
    humanLabel: 'List categories',
    mutating: false,
    description: "Categories already in use, so new blocks reuse the user's own vocabulary.",
    parameters: schema({}),
  },
  {
    name: TOOL_NAMES.SUGGEST_RECOMMENDATION,
    humanLabel: 'Post an optional suggestion card',
    mutating: true,
    description:
      "Post one optional suggestion to the user's Today screen for them to accept or dismiss. Use " +
      'at most once per conversation and only with evidence from analytics. This does not change ' +
      'the schedule.',
    parameters: schema(
      {
        title: str('Under 60 characters, the headline of the suggestion.'),
        body: str('One or two sentences: the observation and the concrete change.'),
        kind: str('Suggestion category.', ['schedule', 'focus', 'rest', 'streak', 'habit', 'general']),
      },
      ['title', 'body'],
    ),
  },
]

export const TOOLS_BY_NAME = new Map(ALL_TOOLS.map((tool) => [tool.name, tool]))

export const ALL_TOOL_NAMES = ALL_TOOLS.map((tool) => tool.name)

export const RECOMMENDATION_TOOL_NAMES = [
  TOOL_NAMES.GET_ANALYTICS,
  TOOL_NAMES.GET_SCHEDULE,
  TOOL_NAMES.GET_PROGRESS,
  TOOL_NAMES.SUGGEST_RECOMMENDATION,
] as string[]
