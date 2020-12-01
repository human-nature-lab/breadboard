<template>
  <div
    class="relative w-full h-full">
    <draggable
      v-model="items"
      :disabled="locked"
      group="money"
      :sort="false"
      @start="start"
      @end="end"
      @change="onChange"
      :clone="onClone"
      draggable=".drag-item"
      class="absolute w-full h-full z-10"
      v-show="visible">
      <div
        class="absolute drag-item"
        v-for="item in items"
        :style="currencyStyle(item.index)"
        :key="item.key">
        <slot name="item" />
      </div>
    </draggable>
    <slot />
  </div>
</template>

<script lang="ts">
import Vue from "vue";
import draggable from 'vuedraggable'

export default Vue.extend({
  name: 'CurrencyDropZone',
  components: { draggable },
  props: {
    value: {
      type: Number,
      required: true
    },
    visible: {
      type: Boolean,
      default: true,
    },
    xOffset: {
      type: Number,
      default: 5,
    },
    yOffset: {
      type: Number,
      default: 5,
    },
    rotate: {
      type: Number,
      required: false
    },
    group: {
      type: String,
      required: true
    },
    locked: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      originalValue: this.value,  // used to maintain the original layout even when the value changes
      willAccept: false,
      items: [] as { key: string, index: number }[]
    }
  },
  created () {
    this.updateItems()
  },
  watch: {
    value (newVal: number) {
      if (this.items.length !== newVal) {
        this.updateItems()
        this.originalValue = this.value
      }
    }
  },
  methods: {
    onClone (original: any) {
      console.log('clone', original)
      return original
    },
    onChange () {
      console.log('onChange', arguments)
      this.$emit('input', this.items.length)
    },
    start () {
      console.log('drag start', arguments)
    },
    end () {
      console.log('drag end', arguments)
      // this.updateItems()
    },
    updateItems () {
      console.log('updating items')
      this.items = new Array(this.value).fill(0).map((_, i) => ({
        key: this.group + i,
        index: i + 1
      }))
    },
    onDragStart(ev: DragEventInit) {
      console.log("ondragstart", ev);
      const data = {
        group: 'money',
        value: 1,
      };
      ev.dataTransfer!.effectAllowed = "move";
      ev.dataTransfer!.setData("currency", JSON.stringify(data));
      // this.$emit('input', this.value - data.value)
    },
    onDragOver(ev: DragEvent) {
      console.log("dragover", ev);
      // Call ev.preventDefault() if this item can be dropped here
      if (ev.dataTransfer && ev.dataTransfer.types.includes("currency")) {
        ev.preventDefault();
      }
    },
    onDragEnter(ev: DragEvent) {
      console.log("dragenter", ev);
    },
    onDragEnd(ev: DragEvent) {
      console.log(
        "dragend",
        ev,
        ev.dataTransfer!.dropEffect,
        ev.dataTransfer!.effectAllowed,
        ev.dataTransfer!.types.includes("currency")
      );
      if (ev.dataTransfer && ev.dataTransfer.dropEffect !== "none") {
        console.log(JSON.stringify(ev.dataTransfer));
        this.$emit("input", this.value - 1);
      }
    },
    onDragLeave(ev: DragEvent) {
      console.log("dragleave", ev);
    },
    onDrop(ev: DragEvent) {
      console.log(
        "onDrop",
        ev,
        ev.dataTransfer!.dropEffect,
        ev.dataTransfer!.effectAllowed
      );
      if (ev.dataTransfer) {
        try {
          const data = JSON.parse(ev.dataTransfer.getData("currency"));
          if (data.value) {
            this.$emit("input", this.value + data.value);
          }
        } catch (err) {
          console.error(err);
        }
      }
    },
    currencyStyle(i: number) {
      const center = Math.ceil(this.originalValue / 2);
      let transform = `translate(${this.xOffset * (i - center)}px, ${
        this.yOffset * (center - i)
      }px)`;
      if (this.rotate) {
        transform += ` rotate(${this.rotate}deg)`;
      }
      return {
        transform,
      };
    },
  },
});
</script>

<style lang="sass">
  
</style>