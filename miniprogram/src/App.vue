<script>
export default {
  onLaunch() {},
  onShow() {},
  onHide() {}
}
</script>

<style lang="scss">
/* 全局公共样式 */
page {
  background: $bg-page;
  color: $text-main;
  font-size: 28rpx;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* 白卡片 */
.card {
  background: $bg-card;
  border-radius: $radius-card;
  padding: 32rpx;
  box-shadow: 0 2rpx 12rpx rgba(17, 24, 39, 0.04);
}

/* 区块标题 */
.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: $text-main;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

/* 渐变主按钮 */
.btn-gradient {
  background: $brand-gradient;
  color: #ffffff;
  border-radius: $radius-btn;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
  padding: 22rpx 0;
  border: none;
  line-height: 1.5;

  &::after {
    border: none;
  }

  &[disabled] {
    opacity: 0.5;
    color: #ffffff;
  }
}

/* 白底描边按钮 */
.btn-plain {
  background: #ffffff;
  color: $text-main;
  border: 2rpx solid $border-color;
  border-radius: $radius-btn;
  font-size: 30rpx;
  text-align: center;
  padding: 20rpx 0;
  line-height: 1.5;

  &::after {
    border: none;
  }
}

/* 状态徽章 */
.badge {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 18rpx;
  border-radius: $radius-pill;
  font-size: 22rpx;
  line-height: 1.6;

  &.badge-green { color: $green; background: $green-bg; }
  &.badge-yellow { color: $yellow; background: $yellow-bg; }
  &.badge-red { color: $red; background: $red-bg; }
  &.badge-blue { color: $blue; background: $blue-bg; }
  &.badge-purple { color: $purple; background: $purple-bg; }
  &.badge-gray { color: $gray; background: $gray-bg; }
}

/* 小标签 chip */
.chip {
  display: inline-flex;
  padding: 4rpx 16rpx;
  border-radius: $radius-pill;
  font-size: 22rpx;
  color: $text-sub;
  background: $gray-bg;
}

/* 胶囊筛选 pill */
.pill {
  display: inline-flex;
  padding: 10rpx 28rpx;
  border-radius: $radius-pill;
  font-size: 26rpx;
  color: $text-sub;
  background: #ffffff;
  border: 2rpx solid $border-color;

  &.active {
    color: #ffffff;
    background: $brand-gradient;
    border-color: transparent;
  }
}

/* 次要文字 */
.muted {
  color: $text-sub;
  font-size: 26rpx;
}

/* 空状态 */
.empty-box {
  padding: 120rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  color: $text-light;
  font-size: 26rpx;

  .empty-icon {
    font-size: 80rpx;
    line-height: 1;
  }
}

/* 进度条 */
.progress-track {
  height: 14rpx;
  background: $gray-bg;
  border-radius: $radius-pill;
  overflow: hidden;

  .progress-fill {
    height: 100%;
    background: $brand-gradient;
    border-radius: $radius-pill;
    transition: width 0.3s;
  }
}

/* 单行/两行省略 */
.ellipsis {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ellipsis-2 {
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
