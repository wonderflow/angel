package com.tencent.angel.ml.model

import java.util.concurrent.Future
import java.util.{ArrayList, List}
import com.tencent.angel.conf.MatrixConf
import com.tencent.angel.exception.{AngelException, InvalidParameterException}
import com.tencent.angel.ml.math.TVector
import com.tencent.angel.ml.matrix.MatrixContext
import com.tencent.angel.ml.matrix.psf.get.base.{GetFunc, GetResult}
import com.tencent.angel.ml.matrix.psf.update.enhance.ZeroUpdate.ZeroUpdateParam
import com.tencent.angel.ml.matrix.psf.update.enhance.{UpdateFunc, VoidResult, ZeroUpdate}
import com.tencent.angel.protobuf.generated.MLProtos
import com.tencent.angel.psagent.matrix.transport.adapter.{GetRowsResult, RowIndex}
import com.tencent.angel.worker.task.TaskContext
import org.apache.commons.logging.{Log, LogFactory}

import scala.collection.mutable.Map

/**
  * Angel's Core Abstraction. PSModel is used on workers to manipulate distribute model(matrix) partitions on PSServer.
  *
  * @param modelName matrix name
  * @param row matrix row number
  * @param col matrix column number
  * @param blockRow matrix partition row number
  * @param blockCol matrix partition column number
  * @param ctx Task context
  * @tparam K matrix row type
  */
class PSModel[K <: TVector](val modelName: String, row: Int, col: Int, blockRow: Int = -1, blockCol: Int = -1, var needSave: Boolean = true)(implicit ctx:TaskContext)  {

  val LOG: Log = LogFactory.getLog(classOf[PSModel[K]])

  /** Matrix configuration */
  val matrixCtx =  new MatrixContext(modelName, row, col, blockRow, blockCol)

  /** Get task context */
  def getTaskContext = ctx

  /** Get matrix context */
  def getContext = matrixCtx

  /** Get ps matrix client */
  def getClient = ctx.getMatrix(modelName)

  // =======================================================================
  // Get and Set Area
  // =======================================================================


  /**
    * Get matrix id
    *
    * @return matrix id
    */
  def getMatrixId(): Int = {
      return getClient.getMatrixId
  }

  /**
    * Set model need to be saved
    *
    * @param _needSave
    * @return
    */
  def setNeedSave(_needSave: Boolean):this.type = {
    this.needSave = _needSave
    this
  }
  /**
    * Set matrix attribute
    *
    * @param key attribute name
    * @param value attribute value
    */
  def setAttribute(key: String, value: String):this.type={
    matrixCtx.set(key, value)
    this
  }

  /**
    * Set the average attribute.
    *
    * @param aver true means the matrix update should be divided by total task number before sent to ps
    */
  def setAverage(aver: Boolean):this.type = {
    matrixCtx.set(MatrixConf.MATRIX_AVERAGE, String.valueOf(aver))
    this
  }

  /**
    * Set the hogwild attribute
    *
    * @param hogwild true means use the hogwild mode
    */
  def setHogwild(hogwild: Boolean):this.type = {
    matrixCtx.set(MatrixConf.MATRIX_HOGWILD, String.valueOf(hogwild))
    this
  }

  /**
    * Set the matrix update storage type
    *
    * @param oplogType storage type
    */
  def setOplogType(oplogType: String):this.type = {
    matrixCtx.set(MatrixConf.MATRIX_OPLOG_TYPE, oplogType)
    this
  }

  /**
    * Set the matrix row type
    *
    * @param rowType row type
    */
  def setRowType(rowType: MLProtos.RowType):this.type = {
    matrixCtx.setRowType(rowType)
    this
  }

  /**
    * Set model load path
    *
    * @param path load path
    */
  def setLoadPath(path: String):this.type = {
    matrixCtx.set(MatrixConf.MATRIX_LOAD_PATH, path)
    LOG.info("Before training, matrix " + this.matrixCtx.getName + " will be loaded from " + path)
    this
  }

  /**
    * Set model save path
    *
    * @param path
    */
  def setSavePath(path: String):this.type = {
    matrixCtx.set(MatrixConf.MATRIX_SAVE_PATH, path)
    LOG.info("After training matrix " + this.matrixCtx.getName + " will be saved to " + path)
    this
  }

  // =======================================================================
  // Sync Area
  // =======================================================================


  /**
    * Flush the cached matrix oplogs to ps and update the clock for the matrix
    *
    * @throws com.tencent.angel.exception.AngelException
    * @return a future result
    */
  @throws(classOf[AngelException])
  def clock(): Future[VoidResult] = {
    try {
      return getClient.clock
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Flush the cached matrix oplogs to ps if needed and update the clock for the matrix
    *
    * @param flush flush the cached oplog first or not
    * @throws com.tencent.angel.exception.AngelException
    * @return a future result
    */
  @throws(classOf[AngelException])
  def clock(flush: Boolean): Future[VoidResult] = {
    try {
      return getClient.clock(flush)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  override def finalize(): Unit = super.finalize()

  /**
    * Flush the cached matrix oplogs to ps
    *
    * @throws com.tencent.angel.exception.AngelException
    * @return a future result
    */
  @throws(classOf[AngelException])
  def flush(): Future[VoidResult] = {
    try {
      return getClient.flush
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Increment the matrix row vector use a same dimension vector. The update will be cache in local
    * and send to ps until flush or clock is called
    *
    * @param delta update row vector
    * @throws com.tencent.angel.exception.AngelException
    */
  @throws(classOf[AngelException])
  def increment(delta: TVector) {
    try {
      getClient.increment(delta)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Increment the matrix row vector use a same dimension vector. The update will be cache in local
    * and send to ps until flush or clock is called
    *
    * @param rowIndex row index
    * @param delta update row vector
    * @throws com.tencent.angel.exception.AngelException
    */
  @throws(classOf[AngelException])
  def increment(rowIndex: Int, delta: TVector) {
    try {
      getClient.increment(rowIndex, delta)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Increment the matrix row vectors use same dimension vectors. The update will be cache in local
    * and send to ps until flush or clock is called
    *
    * @param deltas update row vectors
    * @throws com.tencent.angel.exception.AngelException
    */
  @throws(classOf[AngelException])
  def increment(deltas: List[TVector]) {
    import scala.collection.JavaConversions._
    for (delta <- deltas) increment(delta)
  }

  /**
    * Get any result you want about the matrix use a psf get function
    *
    * @param func psf get function
    * @throws com.tencent.angel.exception.AngelException
    * @return psf get function result
    */
  @throws(classOf[AngelException])
  def get(func: GetFunc): GetResult = {
    try {
      return getClient.get(func)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }


  /**
    * Get a matrix row use row index
    *
    * @param rowIndex row index
    * @throws com.tencent.angel.exception.AngelException
    * @return
    */
  @SuppressWarnings(Array("unchecked"))
  @throws(classOf[AngelException])
  def getRow(rowIndex: Int): K = {
    try {
      return getClient.getRow(rowIndex).asInstanceOf[K]
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Get a batch of matrix rows
    *
    * @param rowIndex row indexes
    * @param batchNum the number of rows get in a rpc
    * @throws com.tencent.angel.exception.AngelException
    * @return row index to row map
    */
  @throws(classOf[AngelException])
  def getRows(rowIndex: RowIndex, batchNum: Int): Map[Int, K] = {
    val indexToVectorMap = scala.collection.mutable.Map[Int, K]()
    val rows  = getRowsFlow(rowIndex, batchNum)
    try {
      var finish = false
      while (!finish) {
        rows.take() match {
          case null => finish = true
          case row => indexToVectorMap += (row.getRowId -> row.asInstanceOf[K])
        }
      }
    }
    catch {
      case e: Exception => {
        throw new AngelException(e)
      }
    }
    indexToVectorMap
  }

  /**
    * Get a batch of matrix rows
    *
    * @param rowIndexes row indexes
    * @throws com.tencent.angel.exception.AngelException
    * @return row list
    */
  @throws(classOf[AngelException])
  def getRows(rowIndexes:Array[Int]): List[K] = {
    val rowIndex = new RowIndex()
    for(index <- rowIndexes) {
      rowIndex.addRowId(index)
    }

    val indexToVectorMap = getRows(rowIndex, -1)

    val rowList = new ArrayList[K](rowIndexes.length)

    for (i <- 0 until rowIndexes.length)
      rowList.add(indexToVectorMap.get(rowIndexes(i)).get)

    rowList
  }

  /**
    * Get a batch of rows use pipeline mode
    *
    * @param rowIndex row indexes
    * @param batchNum the number of rows get in a rpc
    * @throws com.tencent.angel.exception.AngelException
    * @return Get result which contains a blocked queue
    */
  @throws(classOf[AngelException])
  def getRowsFlow(rowIndex: RowIndex, batchNum: Int): GetRowsResult = {
    try {
      return getClient.getRowsFlow(rowIndex, batchNum)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Update the matrix use a update psf
    *
    * @param func update psf
    * @throws com.tencent.angel.exception.AngelException
    * @return a future result
    */
  @throws(classOf[AngelException])
  def update(func: UpdateFunc): Future[VoidResult] = {
    try {
      return getClient.update(func)
    }
    catch {
      case e: InvalidParameterException => {
        throw new AngelException(e)
      }
    }
  }

  /**
    * Set all matrix elements to zero
    *
    * @throws com.tencent.angel.exception.AngelException
    */
  @throws(classOf[AngelException])
  def zero() {
    val updater: ZeroUpdate = new ZeroUpdate(new ZeroUpdateParam(getMatrixId, false))
    try {
      update(updater).get
    }
    catch {
      case e: Any => {
        throw new AngelException(e)
      }
    }
  }

}

object PSModel {
  def apply[K <: TVector](modelName: String, row: Int, col: Int, blockRow: Int = -1, blockCol: Int = -1)(implicit ctx:TaskContext) = {
    new PSModel[K](modelName, row, col, blockRow, blockCol)(ctx)
  }
}

