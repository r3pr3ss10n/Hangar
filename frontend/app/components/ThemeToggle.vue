<script setup lang="ts">
import { Moon, Sun } from 'lucide-vue-next'
import { Button } from '~/components/ui/button'
import { Tooltip, TooltipTrigger, TooltipContent } from '~/components/ui/tooltip'
import { useTheme } from '~/composables/useTheme'
import { useI18n } from '~/composables/useI18n'

const { isDark, toggle } = useTheme()
const { t } = useI18n()
</script>

<template>
  <Tooltip>
    <TooltipTrigger as-child>
      <Button variant="ghost" size="icon-sm" :aria-label="t('theme.toggle')" @click="toggle">
        <Transition
          mode="out-in"
          enter-active-class="transition duration-150"
          enter-from-class="rotate-90 opacity-0"
          leave-active-class="transition duration-150"
          leave-to-class="-rotate-90 opacity-0"
        >
          <Moon v-if="isDark" key="moon" />
          <Sun v-else key="sun" />
        </Transition>
      </Button>
    </TooltipTrigger>
    <TooltipContent>{{ isDark ? t('theme.switchToLight') : t('theme.switchToDark') }}</TooltipContent>
  </Tooltip>
</template>
