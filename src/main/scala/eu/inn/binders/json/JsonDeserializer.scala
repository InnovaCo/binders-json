package eu.inn.binders.json

import java.util.Date

import com.fasterxml.jackson.core.{JsonToken, JsonParser}
import eu.inn.binders.core.Deserializer
import eu.inn.binders.value.Value
import eu.inn.binders.naming.Converter
import scala.collection.mutable.ArrayBuffer
import scala.language.experimental.macros

class JsonDeserializeException(message: String) extends RuntimeException(message)

class JsonDeserializerBase[C <: Converter, I <: Deserializer[C]] (jsonParser: JsonParser, val moveToNextToken: Boolean, val fieldName: Option[String])
  extends Deserializer[C] {

  val currentToken = if (moveToNextToken) nextToken() else jsonParser.getCurrentToken

  def iterator(): Iterator[I] = {
    if (currentToken == JsonToken.START_ARRAY) {
      createArrayIterator
    }
    else if (currentToken == JsonToken.START_OBJECT) {
      createObjectIterator
    }
    else
      throw new JsonDeserializeException("Couldn't iterate nonarray/nonobject field. Current token: " + currentToken)
  }

  protected def createArrayIterator: Iterator[I] = new PrefetchIterator(JsonToken.END_ARRAY, false)

  protected def createObjectIterator: Iterator[I] = new PrefetchIterator(JsonToken.END_OBJECT, true)

  protected class PrefetchIterator(endToken: JsonToken, moveToNextTokenForChildren: Boolean) extends Iterator[I] {
    var _hasNext = true
    var _moveNext = true
    nextIfNeeded()

    override def hasNext: Boolean = {
      nextIfNeeded()
      _hasNext
    }

    override def next(): I = {
      nextIfNeeded()
      _moveNext = true
      createFieldDeserializer(jsonParser, moveToNextTokenForChildren, Some(jsonParser.getCurrentName))
    }

    def nextIfNeeded(): Unit = {
      if (_moveNext && _hasNext) {
        nextToken()
        _moveNext = false
        _hasNext = jsonParser.getCurrentToken != endToken
      }
    }
  }
  
  protected def createFieldDeserializer(jsonParser: JsonParser, moveToNextToken: Boolean, fieldName: Option[String]): I = ??? //new JsonDeserializer[C](jsonNode, fieldName)

  protected def nextToken() = {
    val token = jsonParser.nextToken()
    if (token == null)
      throw new JsonDeserializeException("Unexpected token: " + token + " offset: " + jsonParser.getTokenLocation)
    token
  }

  def isNull: Boolean = jsonParser.getCurrentToken == JsonToken.VALUE_NULL
  def readString(): String = jsonParser.getText
  def readInt(): Int = jsonParser.getIntValue
  def readLong(): Long = jsonParser.getLongValue
  def readDouble(): Double = jsonParser.getDoubleValue
  def readFloat(): Float = jsonParser.getDoubleValue.toFloat
  def readBoolean(): Boolean = jsonParser.getBooleanValue
  def readBigDecimal(): BigDecimal = JsonDeserializer.stringToBigDecimal(jsonParser.getText)
  def readDate(): Date = new Date(jsonParser.getLongValue)

  def readValue(): Value = {
    import eu.inn.binders.value._
    jsonParser.getCurrentToken() match {
      case JsonToken.VALUE_NULL => Null
      case JsonToken.VALUE_TRUE => Bool(true)
      case JsonToken.VALUE_FALSE => Bool(false)
      case JsonToken.VALUE_STRING => Text(jsonParser.getText)
      case JsonToken.VALUE_NUMBER_INT => Number(jsonParser.getDecimalValue)
      case JsonToken.VALUE_NUMBER_FLOAT => Number(jsonParser.getDecimalValue)
      case JsonToken.START_OBJECT => {
        var map = new scala.collection.mutable.HashMap[String, Value]()
        iterator().foreach(i => {
          val d = i.asInstanceOf[JsonDeserializerBase[_,_]]
          map += d.fieldName.get -> d.readValue()
        })
        Obj(map.toMap)
      }
      case JsonToken.START_ARRAY => {
        val array = new ArrayBuffer[Value]()
        iterator().foreach(i => array += i.asInstanceOf[JsonDeserializerBase[_,_]].readValue())
        Lst(array)
      }
      case _ => throw new JsonDeserializeException(s"Can't deserialize token: ${jsonParser.getCurrentToken} at ${jsonParser.getCurrentLocation}")
    }
  }
}

class JsonDeserializer[C <: Converter] (jsonParser: JsonParser, override val moveToNextToken: Boolean = true, override val fieldName: Option[String] = None)
  extends JsonDeserializerBase[C, JsonDeserializer[C]](jsonParser, moveToNextToken, fieldName) {
  protected override def createFieldDeserializer(jsonParser: JsonParser, moveToNextToken: Boolean, fieldName: Option[String]): JsonDeserializer[C] = new JsonDeserializer[C](jsonParser, moveToNextToken, fieldName)
}

object JsonDeserializer {

  private val precision = new java.math.MathContext(150)

  def stringToBigDecimal(s: String): BigDecimal ={
    BigDecimal(s, precision)
  }
}