build-parameter
长度 小于 等于 方法参数长度
executable-parameter
长度 小于 等于 方法参数长度
混长度匹配优先小长度  其次优先参数最优适配
避免出现下边一组满足条件方法：
x(typea,typeb)
x(typec,typed)
typea!=typec & typea instanceof typec
typeb!=typed & typeb instanceof typeb
对于这一类方法默认适配第一个方法。

对于array类型的 如果配置parameter 
则对应参数类型需要是Object[]