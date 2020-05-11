import com.example.demo.controller.ValuesNames
import com.example.demo.controller.XMLCertsController
import java.io.File
import java.lang.StringBuilder

fun main(args: Array<String>) {
    val xml = XMLCertsController()
    val vendor = xml.load("C:\\Users\\admin\\Desktop\\New folder\\im.ca.com-certifications-snmp-CiscoRttMonStatsMib.xml")
    println(vendor.getReadmeText())

}
