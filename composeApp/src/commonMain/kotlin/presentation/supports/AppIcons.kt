package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 应用图标集：SF Symbols 风格的自绘符号——24 pt 视口、圆头圆角描边、常规字重（1.8 px），
 * 需要"实心"语义的（标签栏、选中标记、输入框清除钮）用填充形；形状很简单的（chevron / 加号 / 叉）加粗一档，视觉重量才和复杂符号一致。
 * SF Symbols 的字形只授权在 Apple 平台使用，这里的路径全部自绘，不含 Apple 的字形数据。
 * 品牌标识（Windows / Android / Apple）不是界面符号，保留各自的官方轮廓。
 */
object AppIcons {

    // ---------- 导航与通用动作 ----------

    /** chevron.left：返回。 */
    val ArrowBackIosNew: ImageVector by lazy {
        symbol("chevron.left", autoMirror = true) { stroke(Bold) { polyline(14.8f, 4.8f, 7.6f, 12f, 14.8f, 19.2f) } }
    }

    /** 小一号的 chevron.left：行内的"上一步"。 */
    val KeyboardArrowLeft: ImageVector by lazy {
        symbol("chevron.left.small", autoMirror = true) { stroke(Medium) { polyline(14f, 6.5f, 8.5f, 12f, 14f, 17.5f) } }
    }

    /** chevron.down */
    val ExpandMore: ImageVector by lazy {
        symbol("chevron.down") { stroke(Medium) { polyline(5.5f, 9f, 12f, 15.5f, 18.5f, 9f) } }
    }

    /** chevron.up */
    val ExpandLess: ImageVector by lazy {
        symbol("chevron.up") { stroke(Medium) { polyline(5.5f, 15f, 12f, 8.5f, 18.5f, 15f) } }
    }

    /** xmark */
    val Close: ImageVector by lazy {
        symbol("xmark") { stroke(Medium) { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) } }
    }

    /** xmark.circle.fill：输入框的清除钮。 */
    val Clear: ImageVector by lazy {
        symbol("xmark.circle.fill") {
            fill(evenOdd = true) {
                circle(12f, 12f, 9.6f)
                // 叉是一整块十字形（转 45°）：两条杠分开画的话，even-odd 下交叠处会被重新填上
                val arm = 4.4f
                val half = 0.95f
                val cross = floatArrayOf(
                    -half, -arm, half, -arm, half, -half, arm, -half, arm, half, half, half,
                    half, arm, -half, arm, -half, half, -arm, half, -arm, -half, -half, -half,
                )
                for (i in cross.indices step 2) {
                    val x = 12f + (cross[i] - cross[i + 1]) * Sqrt1_2
                    val y = 12f + (cross[i] + cross[i + 1]) * Sqrt1_2
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }

    /** checkmark */
    val Check: ImageVector by lazy {
        symbol("checkmark") { stroke(Bold) { polyline(5f, 12.8f, 10f, 17.6f, 19f, 6.8f) } }
    }

    /** checkmark.circle.fill：已验证 / 已选中。 */
    val CheckCircle: ImageVector by lazy {
        symbol("checkmark.circle.fill") {
            fill(evenOdd = true) {
                circle(12f, 12f, 9.6f)
                // 对勾的轮廓多边形（2 px 粗，拐角斜接）
                moveTo(8.107f, 11.593f); lineTo(10.655f, 14.141f); lineTo(16.051f, 8.038f)
                lineTo(17.549f, 9.362f); lineTo(10.745f, 17.059f); lineTo(6.693f, 13.007f); close()
            }
        }
    }

    /** plus */
    val Add: ImageVector by lazy {
        symbol("plus") { stroke(Medium) { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) } }
    }

    /** line.3.horizontal */
    val Menu: ImageVector by lazy {
        symbol("line.3.horizontal") { stroke(Medium) { line(4.5f, 7f, 19.5f, 7f); line(4.5f, 12f, 19.5f, 12f); line(4.5f, 17f, 19.5f, 17f) } }
    }

    /** ellipsis：更多操作。 */
    val More: ImageVector by lazy {
        symbol("ellipsis") { fill { circle(5.5f, 12f, 1.7f); circle(12f, 12f, 1.7f); circle(18.5f, 12f, 1.7f) } }
    }

    /** magnifyingglass */
    val Search: ImageVector by lazy {
        symbol("magnifyingglass") { stroke(2f) { circle(10.8f, 10.8f, 6.3f); line(15.5f, 15.5f, 20f, 20f) } }
    }

    /** arrow.clockwise：刷新。 */
    val Refresh: ImageVector by lazy { symbol("arrow.clockwise") { circularArrow() } }

    /** arrow.counterclockwise：重置 / 恢复默认。 */
    val Reset: ImageVector by lazy {
        symbol("arrow.counterclockwise") { group(scaleX = -1f, pivotX = 12f, pivotY = 12f) { circularArrow() } }
    }

    /** square.and.arrow.up：分享。 */
    val Share: ImageVector by lazy {
        symbol("square.and.arrow.up") {
            stroke {
                line(12f, 3f, 12f, 14.5f)
                polyline(8f, 7f, 12f, 3f, 16f, 7f)
                moveTo(8.5f, 10f); lineTo(7.5f, 10f); corner(5f, 12.5f, clockwise = false); lineTo(5f, 18.5f)
                corner(7.5f, 21f, clockwise = false); lineTo(16.5f, 21f); corner(19f, 18.5f, clockwise = false)
                lineTo(19f, 12.5f); corner(16.5f, 10f, clockwise = false); lineTo(15.5f, 10f)
            }
        }
    }

    /** square.and.arrow.down：保存到本地。 */
    val SaveAlt: ImageVector by lazy {
        symbol("square.and.arrow.down") {
            stroke {
                line(12f, 3.5f, 12f, 15f)
                polyline(7.8f, 10.8f, 12f, 15f, 16.2f, 10.8f)
                moveTo(4.5f, 14.5f); lineTo(4.5f, 18f); corner(7f, 20.5f, clockwise = false); lineTo(17f, 20.5f)
                corner(19.5f, 18f, clockwise = false); lineTo(19.5f, 14.5f)
            }
        }
    }

    /** arrow.up.to.line：发布 / 上传。 */
    val Publish: ImageVector by lazy {
        symbol("arrow.up.to.line") {
            stroke { line(5f, 4f, 19f, 4f); line(12f, 20f, 12f, 8.5f); polyline(7.2f, 13.3f, 12f, 8.5f, 16.8f, 13.3f) }
        }
    }

    /** arrow.up.right.square：在外部打开。 */
    val OpenInNew: ImageVector by lazy {
        symbol("arrow.up.right.square", autoMirror = true) {
            stroke {
                moveTo(11f, 5f); lineTo(7f, 5f); corner(4.5f, 7.5f, clockwise = false); lineTo(4.5f, 17f)
                corner(7f, 19.5f, clockwise = false); lineTo(16.5f, 19.5f); corner(19f, 17f, clockwise = false); lineTo(19f, 13f)
                line(11.5f, 12.5f, 20f, 4f)
                polyline(14.5f, 4f, 20f, 4f, 20f, 9.5f)
            }
        }
    }

    /** arrowshape.turn.up.left：回复。 */
    val Reply: ImageVector by lazy {
        symbol("arrowshape.turn.up.left", autoMirror = true) {
            stroke {
                polyline(9f, 5.5f, 4f, 10.5f, 9f, 15.5f)
                moveTo(4f, 10.5f); lineTo(13f, 10.5f); curveTo(17.5f, 10.5f, 20f, 13.5f, 20f, 18.5f)
            }
        }
    }

    /** doc.on.doc：复制。 */
    val ContentCopy: ImageVector by lazy {
        symbol("doc.on.doc") {
            stroke {
                roundRect(4f, 8f, 11f, 13f, 2.5f)
                backSheet(left = 9f, top = 3f, right = 20f, bottom = 16f, frontTop = 8f, frontRight = 15f)
            }
        }
    }

    /** plus.square.on.square：克隆 / 复制一份。 */
    val Duplicate: ImageVector by lazy {
        symbol("plus.square.on.square") {
            stroke {
                roundRect(3.5f, 8f, 12.5f, 12.5f, 2.5f)
                line(9.75f, 11.4f, 9.75f, 17.1f); line(6.9f, 14.25f, 12.6f, 14.25f)
                backSheet(left = 8f, top = 3.5f, right = 20.5f, bottom = 16f, frontTop = 8f, frontRight = 16f)
            }
        }
    }

    /** pencil：编辑。 */
    val Edit: ImageVector by lazy {
        symbol("pencil") {
            stroke {
                moveTo(4f, 20f); lineTo(5.2f, 15.6f); lineTo(16.3f, 4.5f)
                arcTo(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.5f, 7.7f)
                lineTo(8.4f, 18.8f); close()
                line(14.1f, 6.7f, 17.3f, 9.9f)
            }
        }
    }

    /** trash：删除。 */
    val Delete: ImageVector by lazy { symbol("trash") { trash() } }

    /** trash.slash：永久删除。 */
    val DeleteForever: ImageVector by lazy { symbol("trash.slash") { slashed { trash() } } }

    /** nosign：禁止 / 屏蔽。 */
    val Block: ImageVector by lazy {
        symbol("nosign") { stroke { circle(12f, 12f, 9f); line(5.7f, 5.7f, 18.3f, 18.3f) } }
    }

    /** link */
    val Link: ImageVector by lazy {
        symbol("link") {
            group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
                stroke {
                    moveTo(10.2f, 8.4f); lineTo(6.6f, 8.4f)
                    arcTo(3.6f, 3.6f, 0f, isMoreThanHalf = true, isPositiveArc = false, 6.6f, 15.6f); lineTo(10.2f, 15.6f)
                    moveTo(13.8f, 8.4f); lineTo(17.4f, 8.4f)
                    arcTo(3.6f, 3.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.4f, 15.6f); lineTo(13.8f, 15.6f)
                    line(8.6f, 12f, 15.4f, 12f)
                }
            }
        }
    }

    /** questionmark */
    val QuestionMark: ImageVector by lazy {
        symbol("questionmark") {
            stroke(Medium) {
                moveTo(8.2f, 8.6f); curveTo(8.2f, 6.1f, 9.9f, 4.4f, 12.2f, 4.4f); curveTo(14.5f, 4.4f, 16.2f, 5.9f, 16.2f, 8f)
                curveTo(16.2f, 10.2f, 14.4f, 10.9f, 13.2f, 12f); curveTo(12.4f, 12.7f, 12.1f, 13.4f, 12.1f, 14.7f)
            }
            fill { circle(12.1f, 18.8f, 1.3f) }
        }
    }

    // ---------- 标签栏 / 主导航（选中用实心、未选中用描边）----------

    /** house：首页。 */
    val Home: ImageVector by lazy {
        symbol("house") {
            stroke {
                polyline(3.2f, 11.2f, 12f, 3.6f, 20.8f, 11.2f)
                moveTo(5.4f, 9.6f); lineTo(5.4f, 18f); corner(7.9f, 20.5f, clockwise = false); lineTo(16.1f, 20.5f)
                corner(18.6f, 18f, clockwise = false); lineTo(18.6f, 9.6f)
                moveTo(9.8f, 20.5f); lineTo(9.8f, 15.6f); arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11.2f, 14.2f)
                lineTo(12.8f, 14.2f); arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.2f, 15.6f); lineTo(14.2f, 20.5f)
            }
        }
    }

    /** house.fill：标签栏里选中的首页。 */
    val HomeFill: ImageVector by lazy {
        symbol("house.fill") {
            fill(evenOdd = true) {
                moveTo(12f, 2.6f); lineTo(21.4f, 10.6f); curveTo(21.9f, 11f, 21.6f, 11.8f, 21f, 11.8f); lineTo(19.4f, 11.8f)
                lineTo(19.4f, 18.4f); curveTo(19.4f, 19.9f, 18.3f, 21f, 16.8f, 21f); lineTo(7.2f, 21f)
                curveTo(5.7f, 21f, 4.6f, 19.9f, 4.6f, 18.4f); lineTo(4.6f, 11.8f); lineTo(3f, 11.8f)
                curveTo(2.4f, 11.8f, 2.1f, 11f, 2.6f, 10.6f); close()
                // 门洞
                moveTo(9.9f, 21f); lineTo(9.9f, 15.6f); curveTo(9.9f, 14.8f, 10.5f, 14.2f, 11.3f, 14.2f); lineTo(12.7f, 14.2f)
                curveTo(13.5f, 14.2f, 14.1f, 14.8f, 14.1f, 15.6f); lineTo(14.1f, 21f); close()
            }
        }
    }

    /** safari：发现 / 最近访问的世界。 */
    val Explore: ImageVector by lazy {
        symbol("safari") { stroke { circle(12f, 12f, 9.2f); moveTo(15.9f, 8.1f); lineTo(13.5f, 13.5f); lineTo(8.1f, 15.9f); lineTo(10.5f, 10.5f); close() } }
    }

    /** heart.fill：收藏。 */
    val Favorite: ImageVector by lazy { symbol("heart.fill") { fill { heart(inset = 0f) } } }

    /** heart */
    val FavoriteBorder: ImageVector by lazy { symbol("heart") { stroke { heart(inset = 0.7f) } } }

    /** envelope：邀请消息。 */
    val Envelope: ImageVector by lazy {
        symbol("envelope") {
            stroke {
                roundRect(3f, 5.5f, 18f, 13f, 2.5f)
                polyline(3.9f, 7.7f, 12f, 13.3f, 20.1f, 7.7f)
            }
        }
    }

    /** bell：通知。 */
    val Notifications: ImageVector by lazy { symbol("bell") { bell() } }

    /** bell.fill：标签栏里的通知。 */
    val NotificationsFill: ImageVector by lazy {
        symbol("bell.fill") {
            fill {
                moveTo(12f, 2.4f); curveTo(12.8f, 2.4f, 13.4f, 3f, 13.4f, 3.8f); curveTo(16.2f, 4.5f, 18.2f, 7.1f, 18.2f, 10.2f)
                lineTo(18.2f, 13f); curveTo(18.2f, 14.6f, 19f, 15.9f, 20.2f, 17f); curveTo(20.7f, 17.5f, 20.4f, 18.4f, 19.6f, 18.4f)
                lineTo(4.4f, 18.4f); curveTo(3.6f, 18.4f, 3.3f, 17.5f, 3.8f, 17f); curveTo(5f, 15.9f, 5.8f, 14.6f, 5.8f, 13f)
                lineTo(5.8f, 10.2f); curveTo(5.8f, 7.1f, 7.8f, 4.5f, 10.6f, 3.8f); curveTo(10.6f, 3f, 11.2f, 2.4f, 12f, 2.4f); close()
                moveTo(9.3f, 19.9f); lineTo(14.7f, 19.9f); curveTo(14.4f, 21.2f, 13.3f, 22f, 12f, 22f); curveTo(10.7f, 22f, 9.6f, 21.2f, 9.3f, 19.9f); close()
            }
        }
    }

    /** bell.slash：取消订阅通知。 */
    val NotificationsOff: ImageVector by lazy { symbol("bell.slash") { slashed { bell() } } }

    // ---------- 人与群组 ----------

    /** person */
    val Person: ImageVector by lazy {
        symbol("person") {
            stroke { circle(12f, 8f, 3.7f); moveTo(4.8f, 20f); curveTo(4.8f, 15.8f, 7.8f, 13.9f, 12f, 13.9f); curveTo(16.2f, 13.9f, 19.2f, 15.8f, 19.2f, 20f) }
        }
    }

    /** person.2：实例里的人数。 */
    val Group: ImageVector by lazy {
        symbol("person.2") {
            stroke {
                circle(9f, 8.6f, 3.1f)
                moveTo(2.8f, 19.5f); curveTo(2.8f, 15.7f, 5.4f, 14f, 9f, 14f); curveTo(12.6f, 14f, 15.2f, 15.7f, 15.2f, 19.5f)
                circle(16.9f, 9f, 2.5f)
                moveTo(17.4f, 14.2f); curveTo(19.9f, 14.5f, 21.4f, 16.1f, 21.4f, 19.5f)
            }
        }
    }

    /** person.3：群组 / 好友。 */
    val Groups: ImageVector by lazy {
        symbol("person.3") {
            stroke {
                circle(12f, 8.2f, 3f)
                moveTo(6.4f, 19.5f); curveTo(6.4f, 15.8f, 8.7f, 14f, 12f, 14f); curveTo(15.3f, 14f, 17.6f, 15.8f, 17.6f, 19.5f)
                circle(4.9f, 9.9f, 2f)
                moveTo(1.6f, 18.4f); curveTo(1.6f, 16f, 2.8f, 14.7f, 4.8f, 14.4f)
                circle(19.1f, 9.9f, 2f)
                moveTo(22.4f, 18.4f); curveTo(22.4f, 16f, 21.2f, 14.7f, 19.2f, 14.4f)
            }
        }
    }

    /** person.crop.circle：身份牌 / 账号。 */
    val AccountCircle: ImageVector by lazy {
        symbol("person.crop.circle") {
            stroke {
                circle(12f, 12f, 9.2f)
                circle(12f, 9.8f, 3f)
                moveTo(6.3f, 18.7f); curveTo(7.3f, 16.1f, 9.4f, 15f, 12f, 15f); curveTo(14.6f, 15f, 16.7f, 16.1f, 17.7f, 18.7f)
            }
        }
    }

    /** 人 + 放大镜：好友关系网。 */
    val PersonSearch: ImageVector by lazy {
        symbol("person.magnifyingglass") {
            stroke {
                circle(9.5f, 7.8f, 3.3f)
                moveTo(3.3f, 19.5f); curveTo(3.3f, 15.6f, 5.8f, 13.6f, 9.5f, 13.6f); curveTo(10.6f, 13.6f, 11.6f, 13.8f, 12.4f, 14.1f)
                circle(16.6f, 16.2f, 2.9f); line(18.8f, 18.4f, 21.2f, 20.8f)
            }
        }
    }

    /** person.badge.minus：移除好友。 */
    val PersonRemove: ImageVector by lazy {
        symbol("person.badge.minus") {
            stroke {
                circle(9.5f, 8f, 3.4f)
                moveTo(3f, 19.8f); curveTo(3f, 15.8f, 5.6f, 13.9f, 9.5f, 13.9f); curveTo(13.4f, 13.9f, 16f, 15.8f, 16f, 19.8f)
                line(16.8f, 8.5f, 22f, 8.5f)
            }
        }
    }

    /** rectangle.portrait.and.arrow.right：退出登录。 */
    val Logout: ImageVector by lazy {
        symbol("rectangle.portrait.and.arrow.right", autoMirror = true) {
            stroke {
                moveTo(11f, 4f); lineTo(7.5f, 4f); corner(5f, 6.5f, clockwise = false); lineTo(5f, 17.5f)
                corner(7.5f, 20f, clockwise = false); lineTo(11f, 20f)
                line(10f, 12f, 20f, 12f); polyline(16.2f, 8.2f, 20f, 12f, 16.2f, 15.8f)
            }
        }
    }

    // ---------- 账号与安全 ----------

    /** lock */
    val Lock: ImageVector by lazy {
        symbol("lock") {
            stroke {
                roundRect(5f, 10.5f, 14f, 10.5f, 2.5f)
                moveTo(8f, 10.5f); lineTo(8f, 7.5f); arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 7.5f); lineTo(16f, 10.5f)
            }
        }
    }

    /** shield：玩家管理 / 举报。 */
    val Shield: ImageVector by lazy {
        symbol("shield") {
            stroke {
                moveTo(12f, 3f); curveTo(14.2f, 4.6f, 16.6f, 5.4f, 19f, 5.6f); lineTo(19f, 11.2f)
                curveTo(19f, 15.8f, 16.2f, 19.2f, 12f, 21f); curveTo(7.8f, 19.2f, 5f, 15.8f, 5f, 11.2f); lineTo(5f, 5.6f)
                curveTo(7.4f, 5.4f, 9.8f, 4.6f, 12f, 3f); close()
            }
        }
    }

    /** eye */
    val Visibility: ImageVector by lazy { symbol("eye") { eye() } }

    /** eye.slash */
    val VisibilityOff: ImageVector by lazy { symbol("eye.slash") { slashed { eye() } } }

    /** gearshape：设置。 */
    val Settings: ImageVector by lazy {
        symbol("gearshape") {
            stroke {
                // 八齿齿轮：齿顶 / 齿根各取一段圆弧，拐角交给圆角描边
                val outer = 9.6f
                val inner = 7.3f
                for (tooth in 0 until 8) {
                    val center = tooth * 45f
                    val (x1, y1) = polar(12f, 12f, inner, center - 14.5f)
                    if (tooth == 0) moveTo(x1, y1) else arcTo(inner, inner, 0f, isMoreThanHalf = false, isPositiveArc = true, x1, y1)
                    val (x2, y2) = polar(12f, 12f, outer, center - 8.5f)
                    lineTo(x2, y2)
                    val (x3, y3) = polar(12f, 12f, outer, center + 8.5f)
                    arcTo(outer, outer, 0f, isMoreThanHalf = false, isPositiveArc = true, x3, y3)
                    val (x4, y4) = polar(12f, 12f, inner, center + 14.5f)
                    lineTo(x4, y4)
                }
                val (x0, y0) = polar(12f, 12f, inner, -14.5f)
                arcTo(inner, inner, 0f, isMoreThanHalf = false, isPositiveArc = true, x0, y0)
                close()
                circle(12f, 12f, 3.1f)
            }
        }
    }

    // ---------- 内容类型 ----------

    /** photo.on.rectangle：相册。 */
    val Gallery: ImageVector by lazy {
        symbol("photo.on.rectangle") {
            stroke {
                roundRect(3f, 7.5f, 15f, 12f, 2.5f)
                polyline(3.6f, 16.6f, 7.6f, 12.8f, 10.8f, 15.8f, 12.8f, 14f, 17.4f, 18.2f)
                backSheet(left = 7f, top = 4f, right = 21f, bottom = 15.5f, frontTop = 7.5f, frontRight = 18f)
            }
        }
    }

    /** photo.badge.plus：上传图片。 */
    val AddPhoto: ImageVector by lazy {
        symbol("photo.badge.plus") {
            stroke {
                moveTo(13.5f, 6f); lineTo(5.5f, 6f); corner(3f, 8.5f, clockwise = false); lineTo(3f, 16.5f)
                corner(5.5f, 19f, clockwise = false); lineTo(16f, 19f); corner(18.5f, 16.5f, clockwise = false); lineTo(18.5f, 11.5f)
                polyline(3.6f, 16f, 7.8f, 12f, 11.2f, 15.2f, 13.4f, 13.2f, 17.8f, 17.4f)
                line(19.5f, 2.5f, 19.5f, 8.5f); line(16.5f, 5.5f, 22.5f, 5.5f)
            }
        }
    }

    /** archivebox：库存。 */
    val Inventory: ImageVector by lazy {
        symbol("archivebox") {
            stroke {
                roundRect(3f, 4.5f, 18f, 5f, 1.6f)
                moveTo(4.5f, 9.5f); lineTo(4.5f, 17f); corner(7f, 19.5f, clockwise = false); lineTo(17f, 19.5f)
                corner(19.5f, 17f, clockwise = false); lineTo(19.5f, 9.5f)
                line(10f, 13.2f, 14f, 13.2f)
            }
        }
    }

    /** gift：兑换码。 */
    val Redeem: ImageVector by lazy {
        symbol("gift") {
            stroke {
                roundRect(3f, 7.5f, 18f, 4f, 1.4f)
                moveTo(4.5f, 11.5f); lineTo(4.5f, 18f); corner(7f, 20.5f, clockwise = false); lineTo(17f, 20.5f)
                corner(19.5f, 18f, clockwise = false); lineTo(19.5f, 11.5f)
                line(12f, 7.5f, 12f, 20.5f)
                moveTo(12f, 7.5f); curveTo(11.2f, 4.2f, 8.4f, 3f, 7.4f, 4.6f); curveTo(6.5f, 6.2f, 8.6f, 7.5f, 12f, 7.5f)
                curveTo(15.4f, 7.5f, 17.5f, 6.2f, 16.6f, 4.6f); curveTo(15.6f, 3f, 12.8f, 4.2f, 12f, 7.5f)
            }
        }
    }

    /** externaldrive：世界的持久化存储。 */
    val Storage: ImageVector by lazy {
        symbol("externaldrive") {
            stroke { roundRect(3f, 5.5f, 18f, 13f, 3f); line(3f, 13f, 21f, 13f) }
            fill { circle(17.2f, 15.8f, 1.1f) }
        }
    }

    /** number：话题标签 / 其他动作。 */
    val Tag: ImageVector by lazy {
        symbol("number") {
            stroke { line(9.6f, 4f, 7.6f, 20f); line(16.4f, 4f, 14.4f, 20f); line(4.6f, 9f, 20.4f, 9f); line(3.6f, 15f, 19.4f, 15f) }
        }
    }

    /** envelope.open：标为已读。 */
    val MarkRead: ImageVector by lazy {
        symbol("envelope.open") {
            stroke {
                moveTo(3.5f, 10f); lineTo(3.5f, 17.5f); corner(6f, 20f, clockwise = false); lineTo(18f, 20f)
                corner(20.5f, 17.5f, clockwise = false); lineTo(20.5f, 10f)
                polyline(3.5f, 10f, 12f, 3.8f, 20.5f, 10f)
                polyline(3.9f, 10.4f, 12f, 15.2f, 20.1f, 10.4f)
            }
        }
    }

    // ---------- 世界 / 模型信息 ----------

    /** calendar */
    val DateRange: ImageVector by lazy {
        symbol("calendar") {
            stroke { roundRect(3.5f, 5f, 17f, 15.5f, 3f); line(3.5f, 10f, 20.5f, 10f); line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f) }
            fill { circle(8f, 13.8f, 1.05f); circle(12f, 13.8f, 1.05f); circle(16f, 13.8f, 1.05f); circle(8f, 17.2f, 1.05f); circle(12f, 17.2f, 1.05f) }
        }
    }

    /** flame：热度。 */
    val Hot: ImageVector by lazy {
        symbol("flame") {
            stroke {
                moveTo(11.4f, 2.8f); curveTo(11.8f, 5.6f, 13.4f, 7.2f, 15.2f, 9.2f); curveTo(17f, 11.2f, 18f, 13f, 18f, 15.2f)
                curveTo(18f, 18.6f, 15.3f, 21.2f, 12f, 21.2f); curveTo(8.7f, 21.2f, 6f, 18.6f, 6f, 15.4f)
                curveTo(6f, 13.8f, 6.4f, 12.6f, 7f, 11.6f); curveTo(7.6f, 12.4f, 8.4f, 12.9f, 9.6f, 13f)
                curveTo(9f, 9.4f, 9.6f, 5.8f, 11.4f, 2.8f); close()
            }
        }
    }

    /** chart.line.uptrend.xyaxis：人气。 */
    val Trending: ImageVector by lazy {
        symbol("chart.line.uptrend") {
            stroke { polyline(3.5f, 17f, 9f, 11.5f, 13f, 15f, 20.5f, 7f); polyline(15.2f, 7f, 20.5f, 7f, 20.5f, 12.3f) }
        }
    }

    /** flask：社区实验室。 */
    val FlaskConical: ImageVector by lazy {
        symbol("flask") {
            stroke {
                line(8.5f, 3.5f, 15.5f, 3.5f)
                moveTo(10f, 3.5f); lineTo(10f, 9f); lineTo(4.9f, 17.7f); curveTo(4.2f, 19f, 5f, 20.5f, 6.6f, 20.5f)
                lineTo(17.4f, 20.5f); curveTo(19f, 20.5f, 19.8f, 19f, 19.1f, 17.7f); lineTo(14f, 9f); lineTo(14f, 3.5f)
                line(7.3f, 14.5f, 16.7f, 14.5f)
            }
        }
    }

    /** desktopcomputer：PC 平台。 */
    val Computer: ImageVector by lazy {
        symbol("desktopcomputer") { stroke { roundRect(3f, 4f, 18f, 12f, 2.2f); line(12f, 16f, 12f, 19.8f); line(8f, 20f, 16f, 20f) } }
    }

    // ---------- 图片编辑 / 铭牌 ----------

    /** rotate.left：逆时针转 90°。 */
    val RotateLeft: ImageVector by lazy { symbol("rotate.left") { rotateLeft() } }

    /** rotate.right：顺时针转 90°。 */
    val RotateRight: ImageVector by lazy {
        symbol("rotate.right") { group(scaleX = -1f, pivotX = 12f, pivotY = 12f) { rotateLeft() } }
    }

    /** 水平翻转：中轴虚线两侧各一个三角。 */
    val FlipHorizontal: ImageVector by lazy {
        symbol("flip.horizontal") {
            stroke {
                line(12f, 3f, 12f, 5f); line(12f, 8.3f, 12f, 10.3f); line(12f, 13.7f, 12f, 15.7f); line(12f, 19f, 12f, 21f)
                moveTo(8.8f, 6.5f); lineTo(3.5f, 17.5f); lineTo(8.8f, 17.5f); close()
                moveTo(15.2f, 6.5f); lineTo(20.5f, 17.5f); lineTo(15.2f, 17.5f); close()
            }
        }
    }

    /** 镜子：铭牌的镜像展示。 */
    val Mirror: ImageVector by lazy {
        symbol("mirror") { stroke { roundRect(4.5f, 2.5f, 15f, 19f, 2.5f); line(11f, 6.5f, 8.2f, 9.3f); line(15.6f, 7.4f, 8.2f, 14.8f) } }
    }

    /** 屏幕旋转：斜放的设备 + 两段旋转箭头。 */
    val ScreenRotation: ImageVector by lazy {
        symbol("rotate.device") {
            group(rotate = -45f, pivotX = 12f, pivotY = 12f) { stroke { roundRect(8.4f, 5.4f, 7.2f, 13.2f, 1.8f) } }
            stroke {
                arc(12f, 12f, 9.6f, startDeg = -80f, sweepDeg = 62f); arrowHead(12f, 12f, 9.6f, atDeg = -18f, clockwise = true, size = 3.6f)
                arc(12f, 12f, 9.6f, startDeg = 100f, sweepDeg = 62f); arrowHead(12f, 12f, 9.6f, atDeg = 162f, clockwise = true, size = 3.6f)
            }
        }
    }

    // ---------- 窗口控制（Desktop）----------

    /** minus */
    val WindowMinimize: ImageVector by lazy { symbol("minus") { stroke(Medium) { line(6f, 12f, 18f, 12f) } } }

    /** square */
    val WindowMaximize: ImageVector by lazy { symbol("square") { stroke { roundRect(5f, 5f, 14f, 14f, 3f) } } }

    /** square.on.square */
    val WindowRestore: ImageVector by lazy {
        symbol("square.on.square") {
            stroke {
                roundRect(4f, 8f, 12f, 12f, 2.5f)
                backSheet(left = 8f, top = 4f, right = 20f, bottom = 16f, frontTop = 8f, frontRight = 16f)
            }
        }
    }

    // ---------- Boop 表情 ----------

    /** hand.tap：默认的戳一戳。 */
    val Tap: ImageVector by lazy {
        symbol("hand.tap") {
            stroke {
                moveTo(9.2f, 15.2f); lineTo(9.2f, 8.6f); arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12.2f, 8.6f)
                lineTo(12.2f, 12.4f)
                arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.7f, 12.8f)
                arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.2f, 13.4f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.5f, 14.4f)
                lineTo(19.5f, 17f); curveTo(19.5f, 19.6f, 17.6f, 21.4f, 15f, 21.4f); lineTo(12.4f, 21.4f)
                curveTo(10.8f, 21.4f, 9.8f, 20.8f, 8.8f, 19.6f); lineTo(5.6f, 16f)
                curveTo(4.8f, 15f, 5.6f, 13.6f, 6.9f, 14f); curveTo(7.8f, 14.2f, 8.6f, 14.7f, 9.2f, 15.2f)
                arc(10.7f, 8.4f, 4.4f, startDeg = 200f, sweepDeg = 140f)
            }
        }
    }

    /** hand.wave：挥手。 */
    val Wave: ImageVector by lazy {
        symbol("hand.wave") {
            group(rotate = -18f, pivotX = 12f, pivotY = 14f) {
                stroke {
                    moveTo(17.6f, 14.5f); lineTo(17.6f, 9.6f); arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 9.6f)
                    lineTo(15f, 7.4f); arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12.4f, 7.4f)
                    lineTo(12.4f, 6.4f); arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.8f, 6.4f)
                    lineTo(9.8f, 7.9f); arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.2f, 7.9f)
                    lineTo(7.2f, 13f); curveTo(6.3f, 12.1f, 5.1f, 11.2f, 4f, 11.9f); curveTo(3.1f, 12.5f, 3.5f, 13.7f, 4.3f, 14.5f)
                    curveTo(5.9f, 16.2f, 6.5f, 17.4f, 7.3f, 18.9f); curveTo(8.1f, 20.4f, 9.7f, 21.4f, 12f, 21.4f)
                    curveTo(15.4f, 21.4f, 17.6f, 18.9f, 17.6f, 14.5f); close()
                    line(15f, 9.6f, 15f, 12.6f); line(12.4f, 7.4f, 12.4f, 12.4f); line(9.8f, 7.9f, 9.8f, 12.6f)
                }
            }
            stroke { arc(12f, 13f, 10.6f, startDeg = 196f, sweepDeg = 34f); arc(12f, 13f, 10.6f, startDeg = 16f, sweepDeg = 34f) }
        }
    }

    /** hand.thumbsup：点赞。 */
    val ThumbUp: ImageVector by lazy {
        symbol("hand.thumbsup") {
            stroke {
                roundRect(3f, 10.5f, 4f, 10f, 1.3f)
                moveTo(7f, 11.2f); lineTo(10.4f, 4.4f); curveTo(10.8f, 3.5f, 11.8f, 3.3f, 12.6f, 3.8f)
                curveTo(13.7f, 4.5f, 14f, 5.7f, 13.6f, 7f); lineTo(12.8f, 10f); lineTo(18.6f, 10f)
                curveTo(20.2f, 10f, 21.3f, 11.4f, 20.9f, 12.9f); lineTo(19.4f, 18.6f)
                curveTo(19.1f, 19.8f, 18.1f, 20.5f, 16.9f, 20.5f); lineTo(7f, 20.5f)
            }
        }
    }

    /** 大笑的脸。 */
    val FaceLaugh: ImageVector by lazy {
        symbol("face.laugh") {
            stroke {
                circle(12f, 12f, 9.2f)
                moveTo(7.2f, 10.4f); curveTo(7.7f, 9.1f, 9.7f, 9.1f, 10.2f, 10.4f)
                moveTo(13.8f, 10.4f); curveTo(14.3f, 9.1f, 16.3f, 9.1f, 16.8f, 10.4f)
                moveTo(7.6f, 13.6f); lineTo(16.4f, 13.6f); curveTo(16.4f, 16.4f, 14.4f, 18.2f, 12f, 18.2f); curveTo(9.6f, 18.2f, 7.6f, 16.4f, 7.6f, 13.6f); close()
            }
        }
    }

    /** 惊讶的脸。 */
    val FaceSurprised: ImageVector by lazy {
        symbol("face.surprised") {
            stroke { circle(12f, 12f, 9.2f); circle(12f, 15.6f, 2.1f) }
            fill { circle(8.8f, 9.8f, 1.2f); circle(15.2f, 9.8f, 1.2f) }
        }
    }

    /** 思考的脸：一侧眉毛挑起、嘴角斜抿。 */
    val FaceThinking: ImageVector by lazy {
        symbol("face.thinking") {
            stroke { circle(12f, 12f, 9.2f); line(13.4f, 7.9f, 16.6f, 7.1f); line(7.4f, 8.2f, 10.2f, 8.2f); line(9f, 16.2f, 14.6f, 15.2f) }
            fill { circle(8.8f, 10.8f, 1.2f); circle(15.2f, 10.4f, 1.2f) }
        }
    }

    /** 生气的脸。 */
    val FaceAngry: ImageVector by lazy {
        symbol("face.angry") {
            stroke {
                circle(12f, 12f, 9.2f)
                line(7f, 8.2f, 10.4f, 9.6f); line(17f, 8.2f, 13.6f, 9.6f)
                moveTo(8.6f, 17f); curveTo(9.8f, 14.9f, 14.2f, 14.9f, 15.4f, 17f)
            }
            fill { circle(9f, 11.4f, 1.2f); circle(15f, 11.4f, 1.2f) }
        }
    }

    // ---------- 品牌标识 ----------

    val Windows: ImageVector by lazy {
        ImageVector.Builder(
            name = "Windows",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.555f, 1.375f)
                lineTo(0f, 2.237f)
                verticalLineToRelative(5.45f)
                horizontalLineToRelative(6.555f)
                close()
                moveTo(0f, 13.795f)
                lineToRelative(6.555f, 0.933f)
                verticalLineTo(8.313f)
                horizontalLineTo(0f)
                close()
                moveToRelative(7.278f, -5.4f)
                lineToRelative(0.026f, 6.378f)
                lineTo(16f, 16f)
                verticalLineTo(8.395f)
                close()
                moveTo(16f, 0f)
                lineTo(7.33f, 1.244f)
                verticalLineToRelative(6.414f)
                horizontalLineTo(16f)
                close()
            }
        }.build()
    }

    val Android: ImageVector by lazy {
        ImageVector.Builder(
            name = "Android",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(40f, 720f)
                quadToRelative(9f, -107f, 65.5f, -197f)
                reflectiveQuadTo(256f, 380f)
                lineToRelative(-74f, -128f)
                quadToRelative(-6f, -9f, -3f, -19f)
                reflectiveQuadToRelative(13f, -15f)
                quadToRelative(8f, -5f, 18f, -2f)
                reflectiveQuadToRelative(16f, 12f)
                lineToRelative(74f, 128f)
                quadToRelative(86f, -36f, 180f, -36f)
                reflectiveQuadToRelative(180f, 36f)
                lineToRelative(74f, -128f)
                quadToRelative(6f, -9f, 16f, -12f)
                reflectiveQuadToRelative(18f, 2f)
                quadToRelative(10f, 5f, 13f, 15f)
                reflectiveQuadToRelative(-3f, 19f)
                lineToRelative(-74f, 128f)
                quadToRelative(94f, 53f, 150.5f, 143f)
                reflectiveQuadTo(920f, 720f)
                close()
                moveToRelative(240f, -110f)
                quadToRelative(21f, 0f, 35.5f, -14.5f)
                reflectiveQuadTo(330f, 560f)
                reflectiveQuadToRelative(-14.5f, -35.5f)
                reflectiveQuadTo(280f, 510f)
                reflectiveQuadToRelative(-35.5f, 14.5f)
                reflectiveQuadTo(230f, 560f)
                reflectiveQuadToRelative(14.5f, 35.5f)
                reflectiveQuadTo(280f, 610f)
                moveToRelative(400f, 0f)
                quadToRelative(21f, 0f, 35.5f, -14.5f)
                reflectiveQuadTo(730f, 560f)
                reflectiveQuadToRelative(-14.5f, -35.5f)
                reflectiveQuadTo(680f, 510f)
                reflectiveQuadToRelative(-35.5f, 14.5f)
                reflectiveQuadTo(630f, 560f)
                reflectiveQuadToRelative(14.5f, 35.5f)
                reflectiveQuadTo(680f, 610f)
            }
        }.build()
    }

    val Apple: ImageVector by lazy {
        ImageVector.Builder(
            name = "Apple",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.182f, 0.008f)
                curveTo(11.148f, -0.03f, 9.923f, 0.023f, 8.857f, 1.18f)
                curveToRelative(-1.066f, 1.156f, -0.902f, 2.482f, -0.878f, 2.516f)
                reflectiveCurveToRelative(1.52f, 0.087f, 2.475f, -1.258f)
                reflectiveCurveToRelative(0.762f, -2.391f, 0.728f, -2.43f)
                moveToRelative(3.314f, 11.733f)
                curveToRelative(-0.048f, -0.096f, -2.325f, -1.234f, -2.113f, -3.422f)
                reflectiveCurveToRelative(1.675f, -2.789f, 1.698f, -2.854f)
                reflectiveCurveToRelative(-0.597f, -0.79f, -1.254f, -1.157f)
                arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.563f, -0.434f)
                curveToRelative(-0.108f, -0.003f, -0.483f, -0.095f, -1.254f, 0.116f)
                curveToRelative(-0.508f, 0.139f, -1.653f, 0.589f, -1.968f, 0.607f)
                curveToRelative(-0.316f, 0.018f, -1.256f, -0.522f, -2.267f, -0.665f)
                curveToRelative(-0.647f, -0.125f, -1.333f, 0.131f, -1.824f, 0.328f)
                curveToRelative(-0.49f, 0.196f, -1.422f, 0.754f, -2.074f, 2.237f)
                curveToRelative(-0.652f, 1.482f, -0.311f, 3.83f, -0.067f, 4.56f)
                reflectiveCurveToRelative(0.625f, 1.924f, 1.273f, 2.796f)
                curveToRelative(0.576f, 0.984f, 1.34f, 1.667f, 1.659f, 1.899f)
                reflectiveCurveToRelative(1.219f, 0.386f, 1.843f, 0.067f)
                curveToRelative(0.502f, -0.308f, 1.408f, -0.485f, 1.766f, -0.472f)
                curveToRelative(0.357f, 0.013f, 1.061f, 0.154f, 1.782f, 0.539f)
                curveToRelative(0.571f, 0.197f, 1.111f, 0.115f, 1.652f, -0.105f)
                curveToRelative(0.541f, -0.221f, 1.324f, -1.059f, 2.238f, -2.758f)
                quadToRelative(0.52f, -1.185f, 0.473f, -1.282f)
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.182f, 0.008f)
                curveTo(11.148f, -0.03f, 9.923f, 0.023f, 8.857f, 1.18f)
                curveToRelative(-1.066f, 1.156f, -0.902f, 2.482f, -0.878f, 2.516f)
                reflectiveCurveToRelative(1.52f, 0.087f, 2.475f, -1.258f)
                reflectiveCurveToRelative(0.762f, -2.391f, 0.728f, -2.43f)
                moveToRelative(3.314f, 11.733f)
                curveToRelative(-0.048f, -0.096f, -2.325f, -1.234f, -2.113f, -3.422f)
                reflectiveCurveToRelative(1.675f, -2.789f, 1.698f, -2.854f)
                reflectiveCurveToRelative(-0.597f, -0.79f, -1.254f, -1.157f)
                arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.563f, -0.434f)
                curveToRelative(-0.108f, -0.003f, -0.483f, -0.095f, -1.254f, 0.116f)
                curveToRelative(-0.508f, 0.139f, -1.653f, 0.589f, -1.968f, 0.607f)
                curveToRelative(-0.316f, 0.018f, -1.256f, -0.522f, -2.267f, -0.665f)
                curveToRelative(-0.647f, -0.125f, -1.333f, 0.131f, -1.824f, 0.328f)
                curveToRelative(-0.49f, 0.196f, -1.422f, 0.754f, -2.074f, 2.237f)
                curveToRelative(-0.652f, 1.482f, -0.311f, 3.83f, -0.067f, 4.56f)
                reflectiveCurveToRelative(0.625f, 1.924f, 1.273f, 2.796f)
                curveToRelative(0.576f, 0.984f, 1.34f, 1.667f, 1.659f, 1.899f)
                reflectiveCurveToRelative(1.219f, 0.386f, 1.843f, 0.067f)
                curveToRelative(0.502f, -0.308f, 1.408f, -0.485f, 1.766f, -0.472f)
                curveToRelative(0.357f, 0.013f, 1.061f, 0.154f, 1.782f, 0.539f)
                curveToRelative(0.571f, 0.197f, 1.111f, 0.115f, 1.652f, -0.105f)
                curveToRelative(0.541f, -0.221f, 1.324f, -1.059f, 2.238f, -2.758f)
                quadToRelative(0.52f, -1.185f, 0.473f, -1.282f)
            }
        }.build()
    }
}

// ---------- 画法 ----------

/** 常规字重。 */
private const val Regular = 1.8f

/** 形状简单的符号加粗一档。 */
private const val Medium = 2.1f

/** chevron / 对勾这类单笔符号再粗一点，缩到 16 dp 也看得清。 */
private const val Bold = 2.4f

private const val Sqrt1_2 = 0.70710677f

private class SymbolScope(private val builder: ImageVector.Builder) {
    fun stroke(width: Float = Regular, block: PathBuilder.() -> Unit) {
        builder.path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }

    fun fill(evenOdd: Boolean = false, block: PathBuilder.() -> Unit) {
        builder.path(
            fill = SolidColor(Color.Black),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            pathBuilder = block,
        )
    }

    fun group(
        rotate: Float = 0f,
        scaleX: Float = 1f,
        pivotX: Float = 0f,
        pivotY: Float = 0f,
        clip: List<PathNode> = emptyList(),
        block: SymbolScope.() -> Unit,
    ) {
        builder.group(rotate = rotate, pivotX = pivotX, pivotY = pivotY, scaleX = scaleX, clipPathData = clip) {
            SymbolScope(this).block()
        }
    }
}

private fun symbol(name: String, autoMirror: Boolean = false, block: SymbolScope.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).also { SymbolScope(it).block() }.build()

private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

private fun PathBuilder.polyline(vararg points: Float) {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx + r, cy)
    arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, cx - r, cy)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
    val right = x + width
    val bottom = y + height
    moveTo(x + radius, y)
    lineTo(right - radius, y); arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, right, y + radius)
    lineTo(right, bottom - radius); arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, right - radius, bottom)
    lineTo(x + radius, bottom); arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x, bottom - radius)
    lineTo(x, y + radius); arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x + radius, y)
    close()
}

/** 从当前点拐一个 90° 圆角到 ([x], [y])：两点的横纵差就是圆角半径。 */
private fun PathBuilder.corner(x: Float, y: Float, clockwise: Boolean) {
    arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = clockwise, x, y)
}

private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Pair<Float, Float> {
    val rad = deg * PI.toFloat() / 180f
    return (cx + r * cos(rad)) to (cy + r * sin(rad))
}

/** 以屏幕角度（0° 朝右、顺时针为正）画一段圆弧。 */
private fun PathBuilder.arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) {
    val (sx, sy) = polar(cx, cy, r, startDeg)
    val (ex, ey) = polar(cx, cy, r, startDeg + sweepDeg)
    moveTo(sx, sy)
    arcTo(r, r, 0f, isMoreThanHalf = abs(sweepDeg) > 180f, isPositiveArc = sweepDeg > 0f, ex, ey)
}

/** 圆弧末端的箭头：尖在圆周 [atDeg] 处，朝向沿切线。 */
private fun PathBuilder.arrowHead(cx: Float, cy: Float, r: Float, atDeg: Float, clockwise: Boolean, size: Float = 4.8f) {
    val (tipX, tipY) = polar(cx, cy, r, atDeg)
    val heading = if (clockwise) atDeg + 90f else atDeg - 90f
    // 两翼张得比一般箭头开：圆弧本身会朝内侧那一翼弯过去，张角小了两条线就糊成一团
    val (ax, ay) = polar(tipX, tipY, size, heading + 180f - 52f)
    val (bx, by) = polar(tipX, tipY, size, heading + 180f + 52f)
    moveTo(ax, ay); lineTo(tipX, tipY); lineTo(bx, by)
}

/** 叠在后面的那张"纸"：只画露在前一张外面的上边、右边和两小段。 */
private fun PathBuilder.backSheet(left: Float, top: Float, right: Float, bottom: Float, frontTop: Float, frontRight: Float) {
    moveTo(left, frontTop); lineTo(left, top + 2.5f); corner(left + 2.5f, top, clockwise = true)
    lineTo(right - 2.5f, top); corner(right, top + 2.5f, clockwise = true)
    lineTo(right, bottom - 2.5f); corner(right - 2.5f, bottom, clockwise = true); lineTo(frontRight, bottom)
}

private fun PathBuilder.heart(inset: Float) {
    val top = 3.8f + inset
    val side = 3f + inset
    moveTo(12f, 20.6f - inset)
    curveTo(12f, 20.6f - inset, side, 15f, side, 8.9f)
    curveTo(side, 5.9f, 5.3f + inset * 0.6f, top, 7.8f, top)
    curveTo(9.6f, top, 11.2f, 4.8f + inset, 12f, 6.3f + inset)
    curveTo(12.8f, 4.8f + inset, 14.4f, top, 16.2f, top)
    curveTo(18.7f - inset * 0.6f, top, 24f - side, 5.9f, 24f - side, 8.9f)
    curveTo(24f - side, 15f, 12f, 20.6f - inset, 12f, 20.6f - inset)
    close()
}

private fun SymbolScope.circularArrow() {
    stroke(2f) {
        arc(12f, 12.4f, 7.4f, startDeg = -8f, sweepDeg = 306f)
        arrowHead(12f, 12.4f, 7.4f, atDeg = 298f, clockwise = true)
    }
}

private fun SymbolScope.trash() {
    stroke {
        line(4f, 6.5f, 20f, 6.5f)
        moveTo(9f, 6.5f); lineTo(9f, 5.2f); arcTo(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.7f, 3.5f)
        lineTo(13.3f, 3.5f); arcTo(1.7f, 1.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 5.2f); lineTo(15f, 6.5f)
        moveTo(6f, 6.5f); lineTo(6.9f, 18.6f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8.9f, 20.5f)
        lineTo(15.1f, 20.5f); arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.1f, 18.6f); lineTo(18f, 6.5f)
        line(10f, 10.5f, 10f, 16.5f); line(14f, 10.5f, 14f, 16.5f)
    }
}

private fun SymbolScope.bell() {
    stroke {
        line(12f, 2.8f, 12f, 4.2f)
        moveTo(6.2f, 10.2f); curveTo(6.2f, 6.8f, 8.7f, 4.2f, 12f, 4.2f); curveTo(15.3f, 4.2f, 17.8f, 6.8f, 17.8f, 10.2f)
        lineTo(17.8f, 13.2f); curveTo(17.8f, 14.8f, 18.6f, 16.2f, 19.6f, 17.2f); lineTo(4.4f, 17.2f)
        curveTo(5.4f, 16.2f, 6.2f, 14.8f, 6.2f, 13.2f); close()
        moveTo(9.8f, 19.8f); curveTo(10.2f, 21f, 11f, 21.5f, 12f, 21.5f); curveTo(13f, 21.5f, 13.8f, 21f, 14.2f, 19.8f)
    }
}

private fun SymbolScope.eye() {
    stroke {
        moveTo(2.5f, 12f); curveTo(5f, 7.2f, 8.3f, 5.2f, 12f, 5.2f); curveTo(15.7f, 5.2f, 19f, 7.2f, 21.5f, 12f)
        curveTo(19f, 16.8f, 15.7f, 18.8f, 12f, 18.8f); curveTo(8.3f, 18.8f, 5f, 16.8f, 2.5f, 12f); close()
        circle(12f, 12f, 3.1f)
    }
}

private fun SymbolScope.rotateLeft() {
    stroke {
        roundRect(8.5f, 10f, 11.5f, 10.5f, 2.2f)
        moveTo(14f, 5.6f); curveTo(8.6f, 5.2f, 4.6f, 8.6f, 4.6f, 13.8f)
        polyline(1.9f, 11f, 4.6f, 13.8f, 7.3f, 11f)
    }
}

/**
 * 带斜杠的变体：原符号在斜杠右上一侧留出一道缝（和 SF Symbols 的 .slash 一样只留单侧），再画斜杠。
 * 缝用裁剪做：外框顺时针、缝的条带逆时针，非零环绕规则下条带内被挖掉。
 */
private fun SymbolScope.slashed(content: SymbolScope.() -> Unit) {
    group(clip = SlashGap, block = content)
    stroke { line(4.2f, 4.2f, 19.8f, 19.8f) }
}

private val SlashGap: List<PathNode> = PathData {
    moveTo(0f, 0f); lineTo(24f, 0f); lineTo(24f, 24f); lineTo(0f, 24f); close()
    moveTo(2.2f, 2.2f); lineTo(21.8f, 21.8f); lineTo(23.7f, 19.9f); lineTo(4.1f, 0.3f); close()
}
