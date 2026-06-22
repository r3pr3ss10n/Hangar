<script setup lang="ts">
import {
  ComboboxRoot,
  ComboboxAnchor,
  ComboboxTrigger,
  ComboboxPortal,
  ComboboxContent,
  ComboboxInput,
  ComboboxViewport,
  ComboboxItem,
  ComboboxItemIndicator,
  ComboboxEmpty,
} from 'reka-ui'
import { ChevronsUpDown, Search, Check } from 'lucide-vue-next'
import { useI18n } from '~/composables/useI18n'
import { cn } from '~/lib/utils'
import { COUNTRIES, countryByIso, countryFlag } from '~/lib/countries'

/**
 * Telegram-style country picker: a dropdown field that opens a searchable list
 * of countries (flag + name + dial code). The model is the selected country's
 * ISO 3166-1 alpha-2 code. Filtering matches on name, ISO code and dial code.
 */
const model = defineModel<string>({ required: true })

const { t } = useI18n()

const selected = computed(() => countryByIso(model.value))

// Each item carries a denormalised search string so reka-ui's built-in filter
// matches "russia", "ru", "7" and "+7" alike.
const items = COUNTRIES.map((c) => ({
  ...c,
  search: `${c.name} ${c.iso2} ${c.dial} +${c.dial}`,
}))
</script>

<template>
  <ComboboxRoot v-model="model" :reset-search-term-on-blur="true">
    <ComboboxAnchor as-child>
      <ComboboxTrigger
        class="flex h-9 w-full items-center gap-2 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background data-[state=open]:ring-2 data-[state=open]:ring-ring data-[state=open]:ring-offset-2 data-[state=open]:ring-offset-background"
      >
        <span v-if="selected" class="text-base leading-none">{{ countryFlag(selected.iso2) }}</span>
        <span class="flex-1 truncate text-left" :class="selected ? '' : 'text-muted-foreground'">
          {{ selected ? selected.name : t('tg.selectCountry') }}
        </span>
        <span v-if="selected" class="text-muted-foreground">+{{ selected.dial }}</span>
        <ChevronsUpDown class="h-4 w-4 shrink-0 text-muted-foreground" />
      </ComboboxTrigger>
    </ComboboxAnchor>

    <ComboboxPortal>
      <ComboboxContent
        position="popper"
        :side-offset="6"
        class="z-50 w-[var(--reka-combobox-trigger-width)] overflow-hidden rounded-lg border bg-popover text-popover-foreground shadow-lg data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
      >
      <div class="flex items-center gap-2 border-b px-3">
        <Search class="h-4 w-4 shrink-0 text-muted-foreground" />
        <ComboboxInput
          :placeholder="t('tg.searchCountry')"
          class="h-9 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
        />
      </div>
      <ComboboxViewport class="max-h-64 overflow-y-auto p-1">
        <ComboboxEmpty class="px-3 py-6 text-center text-sm text-muted-foreground">
          {{ t('tg.noCountry') }}
        </ComboboxEmpty>
        <ComboboxItem
          v-for="c in items"
          :key="c.iso2"
          :value="c.iso2"
          :text-value="c.search"
          :class="cn(
            'relative flex cursor-pointer select-none items-center gap-2.5 rounded-md px-2 py-1.5 text-sm outline-none transition-colors data-[highlighted]:bg-accent data-[highlighted]:text-accent-foreground',
          )"
        >
          <span class="text-base leading-none">{{ countryFlag(c.iso2) }}</span>
          <span class="flex-1 truncate">{{ c.name }}</span>
          <span class="text-muted-foreground">+{{ c.dial }}</span>
          <ComboboxItemIndicator>
            <Check class="h-4 w-4 text-primary" />
          </ComboboxItemIndicator>
        </ComboboxItem>
      </ComboboxViewport>
      </ComboboxContent>
    </ComboboxPortal>
  </ComboboxRoot>
</template>
