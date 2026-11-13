// macOS CoreGraphics renders the same original primitives as the SVG sources. No dependencies.
import AppKit
import Foundation

let root = URL(fileURLWithPath: CommandLine.arguments[1])
let catalog = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("catalog.json"))) as! [String: Any]
let achievements = catalog["achievements"] as! [[String: Any]]
let leaderboard = catalog["leaderboard"] as! [String: Any]
let records = achievements + [leaderboard]
func color(_ hex: String, gray: Bool = false) -> CGColor {
    let n = UInt32(hex.dropFirst(), radix: 16)!
    let r = CGFloat((n >> 16) & 255) / 255, g = CGFloat((n >> 8) & 255) / 255, b = CGFloat(n & 255) / 255
    let l = r * 0.2126 + g * 0.7152 + b * 0.0722
    return CGColor(red: gray ? l : r, green: gray ? l : g, blue: gray ? l : b, alpha: 1)
}
func canvas(_ width: Int, _ height: Int) -> CGContext {
    let c = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    c.translateBy(x: 0, y: CGFloat(height)); c.scaleBy(x: 1, y: -1)
    return c
}
func save(_ c: CGContext, _ path: String) throws {
    let bitmap = NSBitmapImageRep(cgImage: c.makeImage()!)
    try bitmap.representation(using: .png, properties: [:])!.write(to: root.appendingPathComponent(path))
}
func draw(_ items: [[String: Any]], in c: CGContext, gray: Bool) {
    for s in items {
        func n(_ key: String) -> CGFloat { CGFloat((s[key] as! NSNumber).doubleValue) }
        let path = CGMutablePath()
        switch s["kind"] as! String {
        case "circle":
            path.addEllipse(in: CGRect(x: n("x")-n("r"), y: n("y")-n("r"), width: n("r")*2, height: n("r")*2))
        case "rect":
            path.addRoundedRect(in: CGRect(x: n("x"), y: n("y"), width: n("w"), height: n("h")), cornerWidth: n("rx"), cornerHeight: n("rx"))
        default:
            let points = s["points"] as! [[NSNumber]]
            for (index,p) in points.enumerated() {
                let point=CGPoint(x: p[0].doubleValue, y: p[1].doubleValue)
                if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
            if s["kind"] as! String == "polygon" { path.closeSubpath() }
        }
        if s["fill"] as! String != "none" { c.setFillColor(color(s["fill"] as! String, gray: gray)); c.addPath(path); c.fillPath() }
        if s["stroke"] as! String != "none" {
            c.setStrokeColor(color(s["stroke"] as! String, gray: gray));c.setLineWidth(n("width"));c.setLineJoin(.round);c.setLineCap(.round);c.addPath(path);c.strokePath()
        }
    }
}
let preview = canvas(1200, 800)
preview.setFillColor(color("#F3E8D3"));preview.fill(CGRect(x:0,y:0,width:1200,height:800))
for (index,a) in records.enumerated() {
    let key = a["key"] as! String
    let items = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("source/\(key).json"))) as! [[String:Any]]
    let icon = canvas(512,512);draw(items,in:icon,gray:false);try save(icon,"upload/\(key).png")
    for gray in [false,true] {
        let col = index % 6, row = index / 6
        let x = CGFloat(col*200+(gray ? 102:10)), y = CGFloat(row*340+30)
        preview.saveGState();preview.translateBy(x:x,y:y);preview.scaleBy(x:88/512,y:88/512);draw(items,in:preview,gray:gray);preview.restoreGState()
        preview.saveGState();preview.translateBy(x:x+20,y:y+108);preview.scaleBy(x:48/512,y:48/512);draw(items,in:preview,gray:gray);preview.restoreGState()
    }
    let text = NSAttributedString(string: "\(index+1). \(a["name"] as! String)", attributes: [.font:NSFont.systemFont(ofSize:13,weight:.medium),.foregroundColor:NSColor.darkGray])
    NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(cgContext:preview,flipped:true)
    text.draw(in:CGRect(x:(index%6)*200+8,y:(index/6)*340+210,width:184,height:70));NSGraphicsContext.restoreGraphicsState()
}
try save(preview,"preview/earned-locked-88-and-48.png")
