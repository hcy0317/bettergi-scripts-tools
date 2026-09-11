import {createServer} from 'vite'
const server=await createServer({server:{host:'127.0.0.1',port:5179,strictPort:true,open:false},plugins:[{
  name:'priority-isolated-save',configureServer(server){server.middlewares.use('/__priority-test/save',(req,res)=>{
    if(req.method!=='POST'){res.statusCode=405;res.end();return}
    let raw='';req.on('data',chunk=>{raw+=chunk;if(raw.length>100000)req.destroy()})
    req.on('end',()=>{try{const payload=JSON.parse(raw);res.setHeader('Content-Type','application/json');res.end(JSON.stringify(payload))}catch{res.statusCode=400;res.end()}})
  })}
}]})
await server.listen()
console.log('Priority fixture: http://127.0.0.1:5179/bgi/ui/test/priority-preview.html')
process.stdin.resume()
process.stdin.on('data',async chunk=>{if(chunk.toString().trim()==='stop'){await server.close();process.exit(0)}})
