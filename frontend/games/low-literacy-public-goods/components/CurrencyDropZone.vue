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
      draggable=".drag-item"
      class="absolute w-full h-full z-10"
      v-show="visible">
      <div
        class="absolute drag-item"
        v-for="(item, i) in items"
        :style="currencyStyle(i + 1)"
        :key="item">
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
    value: Number,
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
      willAccept: false,
      items: [] as string[]
    }
  },
  created () {
    this.updateItems()
  },
  watch: {
    value (newVal: number) {
      if (this.items.length !== newVal) {
        this.updateItems()
      }
    }
  },
  methods: {
    onChange () {
      console.log('onChange', arguments)
      this.$emit('input', this.items.length)
    },
    start () {
      console.log('drag start', arguments)
    },
    end () {
      console.log('drag end', arguments)
      this.updateItems()
    },
    updateItems () {
      this.items = new Array(this.value).fill(0).map((_, i) => this.group + i)
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
      const center = Math.ceil(this.value / 2);
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