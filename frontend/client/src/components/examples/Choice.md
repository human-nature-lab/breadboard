Prepend and append arrows in the choice.

```vue
<Choice>
  <template v-slot:prepend>
    <<
  </template>
  <template v-slot:append>
    >>
  </template>
</Choice>
```

Replace the choice text with static text
```vue
<Choice>
  A choice
</Choice>
```

Replace the choice text with a formatted date string
```vue
<Choice>
  <template v-slot:default="{ choice }">
    {{ choice.name | date('YYYY-MM-DD') }}
  </template>
</Choice>
```
