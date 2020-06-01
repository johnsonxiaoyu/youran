import searchUtil from '../searchUtil'

function initFormBean () {
  return {
    _displayText: '',
    joinIndex: null,
    sortType: 1,
    parentId: null,
    parentKey: null,
    detailColumn: null
  }
}

/**
 * 修复排序列数据
 */
function repairDetailOrderForEdit (detailOrder, detailColumnList, customColumnList) {
  if (!detailOrder.detailColumn) {
    let parentItem = searchUtil.findSourceItemById(detailColumnList, detailOrder.parentId)
    if (!parentItem) {
      parentItem = searchUtil.findSourceItemById(customColumnList, detailOrder.parentId)
    }
    detailOrder.detailColumn = parentItem
  }
  detailOrder.joinIndex = detailOrder.detailColumn.joinIndex
  detailOrder.parentKey = detailOrder.detailColumn.key
  detailOrder._displayText = detailOrder.detailColumn._displayText + (detailOrder.sortType === 1 ? '▲' : '▼')
}

export default {
  initFormBean,
  repairDetailOrderForEdit
}
